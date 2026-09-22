package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.TestApplication;
import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadPolicy;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import de.codestev.patreoningest.core.acquisition.ItemStatus;
import de.codestev.patreoningest.core.acquisition.ProviderSettings;
import de.codestev.patreoningest.core.acquisition.ProviderSettingsRepository;
import de.codestev.patreoningest.core.acquisition.SourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Deliberately NOT @Transactional: the queue tick dispatches real work onto
// a separate virtual thread with its own connection/transaction, which
// would never see fixture rows created inside a test's still-uncommitted
// transaction. Cleaned up manually in @AfterEach instead.
@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Import(RunDownloadQueueTickUseCaseTest.TestConfig.class)
class RunDownloadQueueTickUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RunDownloadQueueTickUseCase runDownloadQueueTickUseCase;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    @Autowired
    private ProviderSettingsRepository providerSettingsRepository;

    @Autowired
    private AppSettingsRepository appSettingsRepository;

    @Autowired
    private DownloadConcurrencyTracker concurrencyTracker;

    @Autowired
    private FakeSourceDownloader fakeSourceDownloader;

    @MockitoBean
    private RcloneDriveDownloadAdapter rcloneDriveDownloadAdapter;

    @TestConfiguration
    static class TestConfig {
        @Bean
        FakeSourceDownloader fakeSourceDownloader() {
            return new FakeSourceDownloader();
        }
    }

    @AfterEach
    void cleanUp() {
        fakeSourceDownloader.reset();
        while (concurrencyTracker.current() > 0) {
            concurrencyTracker.release();
        }
        downloadItemRepository.deleteAll();
        downloadSourceRepository.deleteAll();
        providerSettingsRepository.deleteAll();
        AppSettings settings = appSettingsRepository.findById(1L).orElseThrow();
        settings.update(1, null, true, null, null, false);
        appSettingsRepository.save(settings);
    }

    private DownloadItem pendingItem(String creator, String url) {
        DownloadSource source = new DownloadSource(creator, null, null,
                SourceType.DRIVE, url, ClaimType.NONE);
        source.markClaimed();
        downloadSourceRepository.save(source);
        return downloadItemRepository.save(new DownloadItem(source, "Model", null));
    }

    @Test
    void anEagerPolicyItemGetsDispatchedAndDownloaded() {
        providerSettingsRepository.save(new ProviderSettings("bulkamancer", DownloadPolicy.EAGER));
        DownloadItem item = pendingItem("bulkamancer", "https://drive.example/eager");
        fakeSourceDownloader.willSucceedWith(new DownloadResult("/downloads/bulkamancer/Model", 1L));

        runDownloadQueueTickUseCase.tick();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(downloadItemRepository.findById(item.getId()).orElseThrow().getStatus())
                        .isEqualTo(ItemStatus.DOWNLOADED));
    }

    @Test
    void aManualPolicyItemIsNotAutomaticallyDispatched() {
        providerSettingsRepository.save(new ProviderSettings("bulkamancer", DownloadPolicy.MANUAL));
        DownloadItem item = pendingItem("bulkamancer", "https://drive.example/manual");

        runDownloadQueueTickUseCase.tick();

        assertThat(fakeSourceDownloader.callCount()).isZero();
        assertThat(downloadItemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(ItemStatus.PENDING);
    }

    @Test
    void dispatchCountNeverExceedsMaxConcurrentDownloads() {
        AppSettings settings = appSettingsRepository.findById(1L).orElseThrow();
        settings.update(2, null, true, null, null, false);
        appSettingsRepository.save(settings);

        providerSettingsRepository.save(new ProviderSettings("bulkamancer", DownloadPolicy.EAGER));
        pendingItem("bulkamancer", "https://drive.example/one");
        pendingItem("bulkamancer", "https://drive.example/two");
        pendingItem("bulkamancer", "https://drive.example/three");
        fakeSourceDownloader.withDelay(300);
        fakeSourceDownloader.willSucceedWith(new DownloadResult("/downloads/bulkamancer/Model", 1L));

        runDownloadQueueTickUseCase.tick();

        assertThat(concurrencyTracker.current()).isEqualTo(2);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(concurrencyTracker.current()).isZero());
    }
}
