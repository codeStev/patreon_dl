package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.TestApplication;
import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import de.codestev.patreoningest.core.acquisition.ItemStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Deliberately NOT @Transactional - see RunDownloadQueueTickUseCaseTest for
// why: dispatched work runs on a separate thread/connection that would
// never see fixture rows stuck in an uncommitted test transaction.
@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Import(TriggerManualDownloadUseCaseTest.TestConfig.class)
class TriggerManualDownloadUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private TriggerManualDownloadUseCase triggerManualDownloadUseCase;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

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
        // The tracker is a singleton bean, not reset between tests - make
        // sure a test that acquired a slot doesn't leak it into the next
        // one. DB rows are deleted manually since there's no transaction
        // rollback to rely on here (see the class comment above).
        fakeSourceDownloader.reset();
        while (concurrencyTracker.current() > 0) {
            concurrencyTracker.release();
        }
        downloadItemRepository.deleteAll();
        downloadSourceRepository.deleteAll();
    }

    private DownloadItem claimedPendingItem(String url) {
        DownloadSource source = new DownloadSource("wicked", null, null,
                SourceType.DRIVE, url, ClaimType.NONE);
        source.markClaimed();
        downloadSourceRepository.save(source);
        return downloadItemRepository.save(new DownloadItem(source, "Model", null));
    }

    @Test
    void dispatchesAManualPolicyItemBypassingTheEagerOnlyFilter() {
        DownloadItem item = claimedPendingItem("https://drive.example/manual-trigger");
        fakeSourceDownloader.willSucceedWith(new DownloadResult("/downloads/wicked/Model", 1L));

        TriggerManualDownloadUseCase.Result result = triggerManualDownloadUseCase.trigger(item.getId());

        assertThat(result).isEqualTo(TriggerManualDownloadUseCase.Result.DISPATCHED);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(downloadItemRepository.findById(item.getId()).orElseThrow().getStatus())
                        .isEqualTo(ItemStatus.DOWNLOADED));
    }

    @Test
    void rejectsAnAlreadyDownloadedItem() {
        DownloadItem item = claimedPendingItem("https://drive.example/already-done");
        item.markDownloaded("/downloads/wicked/Model", 1L);
        downloadItemRepository.save(item);

        assertThat(triggerManualDownloadUseCase.trigger(item.getId()))
                .isEqualTo(TriggerManualDownloadUseCase.Result.ALREADY_DOWNLOADED);
    }

    @Test
    void rejectsAnItemWhoseSourceIsNotClaimedYet() {
        DownloadSource source = new DownloadSource("wicked", null, null,
                SourceType.DRIVE, "https://drive.example/discovered", ClaimType.NONE);
        downloadSourceRepository.save(source);
        DownloadItem item = downloadItemRepository.save(new DownloadItem(source, "Model", null));

        assertThat(triggerManualDownloadUseCase.trigger(item.getId()))
                .isEqualTo(TriggerManualDownloadUseCase.Result.NOT_CLAIMED);
    }

    @Test
    void rejectsAnItemOnALinkDeadSource() {
        DownloadItem item = claimedPendingItem("https://drive.example/dead");
        item.getSource().markLinkDead();
        downloadSourceRepository.save(item.getSource());

        assertThat(triggerManualDownloadUseCase.trigger(item.getId()))
                .isEqualTo(TriggerManualDownloadUseCase.Result.LINK_DEAD);
    }

    @Test
    void resetsAFailedItemBeforeRetrying() {
        DownloadItem item = claimedPendingItem("https://drive.example/retry");
        item.markFailedPermanently("previous error");
        downloadItemRepository.save(item);
        fakeSourceDownloader.willSucceedWith(new DownloadResult("/downloads/wicked/Model", 1L));

        TriggerManualDownloadUseCase.Result result = triggerManualDownloadUseCase.trigger(item.getId());

        assertThat(result).isEqualTo(TriggerManualDownloadUseCase.Result.DISPATCHED);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            DownloadItem reloaded = downloadItemRepository.findById(item.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(ItemStatus.DOWNLOADED);
            assertThat(reloaded.getRetryCount()).isZero();
        });
    }

    @Test
    void returnsConcurrencyLimitReachedWhenNoSlotIsAvailable() {
        DownloadItem item = claimedPendingItem("https://drive.example/busy");
        assertThat(concurrencyTracker.tryAcquire(1)).isTrue();

        assertThat(triggerManualDownloadUseCase.trigger(item.getId()))
                .isEqualTo(TriggerManualDownloadUseCase.Result.CONCURRENCY_LIMIT_REACHED);
    }

    @Test
    void returnsNotFoundForAMissingItem() {
        assertThat(triggerManualDownloadUseCase.trigger(UUID.randomUUID()))
                .isEqualTo(TriggerManualDownloadUseCase.Result.NOT_FOUND);
    }
}
