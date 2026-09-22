package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.TestApplication;
import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import de.codestev.patreoningest.core.acquisition.ItemStatus;
import de.codestev.patreoningest.core.acquisition.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Import(ExecuteDownloadUseCaseTest.TestConfig.class)
@Transactional
class ExecuteDownloadUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ExecuteDownloadUseCase executeDownloadUseCase;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    @Autowired
    private FakeSourceDownloader fakeSourceDownloader;

    // Neutralizes the real adapter for this List<SourceDownloader> injection
    // point - an unstubbed mock's supports() returns null, never DRIVE.
    @MockitoBean
    private RcloneDriveDownloadAdapter rcloneDriveDownloadAdapter;

    @TestConfiguration
    static class TestConfig {
        @Bean
        FakeSourceDownloader fakeSourceDownloader() {
            return new FakeSourceDownloader();
        }
    }

    private DownloadItem pendingItem(String sourceUrl) {
        DownloadSource source = new DownloadSource("bulkamancer", null, null,
                SourceType.DRIVE, sourceUrl, ClaimType.NONE);
        source.markClaimed();
        downloadSourceRepository.save(source);
        DownloadItem item = new DownloadItem(source, "Test Model", null);
        return downloadItemRepository.save(item);
    }

    @Test
    void successfulFetchMarksTheItemDownloaded() {
        DownloadItem item = pendingItem("https://drive.example/folder/success");
        fakeSourceDownloader.willSucceedWith(new DownloadResult("/downloads/bulkamancer/Test Model", 12345L));

        executeDownloadUseCase.execute(item.getId());

        DownloadItem reloaded = downloadItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ItemStatus.DOWNLOADED);
        assertThat(reloaded.getLocalPath()).isEqualTo("/downloads/bulkamancer/Test Model");
        assertThat(reloaded.getFileSizeBytes()).isEqualTo(12345L);
    }

    @Test
    void transientFailureStaysPendingAndSchedulesABackoffRetry() {
        DownloadItem item = pendingItem("https://drive.example/folder/transient");
        fakeSourceDownloader.willFailWith(new DownloadFailedException("temporary blip", false));

        executeDownloadUseCase.execute(item.getId());

        DownloadItem reloaded = downloadItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ItemStatus.PENDING);
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(reloaded.getLastError()).isEqualTo("temporary blip");
    }

    @Test
    void permanentFailureMarksItemFailedAndFlagsTheSourceLinkDead() {
        DownloadItem item = pendingItem("https://drive.example/folder/dead-link");
        fakeSourceDownloader.willFailWith(new DownloadFailedException("404 not found", true));

        executeDownloadUseCase.execute(item.getId());

        DownloadItem reloaded = downloadItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ItemStatus.FAILED);
        assertThat(downloadSourceRepository.findById(reloaded.getSource().getId()))
                .hasValueSatisfying(source -> assertThat(source.isLinkDead()).isTrue());
    }

    @Test
    void exceedingMaxRetryAttemptsGivesUpPermanentlyWithoutFlaggingTheSource() {
        DownloadItem item = pendingItem("https://drive.example/folder/give-up");
        for (int i = 0; i < ExecuteDownloadUseCase.MAX_ATTEMPTS; i++) {
            item.recordTransientFailure("earlier failure", LocalDateTime.now().minusMinutes(1));
        }
        downloadItemRepository.save(item);
        fakeSourceDownloader.willFailWith(new DownloadFailedException("still broken", false));

        executeDownloadUseCase.execute(item.getId());

        DownloadItem reloaded = downloadItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ItemStatus.FAILED);
        assertThat(downloadSourceRepository.findById(reloaded.getSource().getId()))
                .hasValueSatisfying(source -> assertThat(source.isLinkDead()).isFalse());
    }

    @Test
    void aMissingItemIsSkippedWithoutError() {
        executeDownloadUseCase.execute(java.util.UUID.randomUUID());
        // No exception - just proves the missing-item guard doesn't blow up.
    }
}
