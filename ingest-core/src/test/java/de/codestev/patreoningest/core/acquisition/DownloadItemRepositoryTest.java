package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Transactional
class DownloadItemRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    private DownloadSource claimedSource(String url) {
        DownloadSource source = new DownloadSource("bulkamancer", null, null,
                SourceType.DRIVE, url, ClaimType.NONE);
        source.markClaimed();
        return downloadSourceRepository.save(source);
    }

    @Test
    void eligibleItemIsAPendingItemOnAClaimedNonDeadSourceWithNoFutureBackoff() {
        DownloadSource source = claimedSource("https://drive.example/eligible");
        DownloadItem item = downloadItemRepository.save(new DownloadItem(source, "Model", null));

        assertThat(downloadItemRepository.findEligibleForAutomaticDownload(LocalDateTime.now()))
                .extracting(DownloadItem::getId)
                .containsExactly(item.getId());
    }

    @Test
    void alreadyDownloadedItemsAreExcluded() {
        DownloadSource source = claimedSource("https://drive.example/downloaded");
        DownloadItem item = new DownloadItem(source, "Model", null);
        item.markDownloaded("/downloads/model", 10L);
        downloadItemRepository.save(item);

        assertThat(downloadItemRepository.findEligibleForAutomaticDownload(LocalDateTime.now())).isEmpty();
    }

    @Test
    void itemsOnAnUnclaimedSourceAreExcluded() {
        DownloadSource source = new DownloadSource("bulkamancer", null, null,
                SourceType.DRIVE, "https://drive.example/discovered", ClaimType.NONE);
        downloadSourceRepository.save(source);
        downloadItemRepository.save(new DownloadItem(source, "Model", null));

        assertThat(downloadItemRepository.findEligibleForAutomaticDownload(LocalDateTime.now())).isEmpty();
    }

    @Test
    void itemsOnALinkDeadSourceAreExcluded() {
        DownloadSource source = claimedSource("https://drive.example/dead");
        source.markLinkDead();
        downloadSourceRepository.save(source);
        downloadItemRepository.save(new DownloadItem(source, "Model", null));

        assertThat(downloadItemRepository.findEligibleForAutomaticDownload(LocalDateTime.now())).isEmpty();
    }

    @Test
    void itemsStillWaitingOnABackoffWindowAreExcludedUntilItPasses() {
        DownloadSource source = claimedSource("https://drive.example/backoff");
        DownloadItem item = new DownloadItem(source, "Model", null);
        item.recordTransientFailure("blip", LocalDateTime.now().plusMinutes(5));
        downloadItemRepository.save(item);

        assertThat(downloadItemRepository.findEligibleForAutomaticDownload(LocalDateTime.now())).isEmpty();
        assertThat(downloadItemRepository.findEligibleForAutomaticDownload(LocalDateTime.now().plusMinutes(6)))
                .extracting(DownloadItem::getId)
                .containsExactly(item.getId());
    }

    @Test
    void resultsAreOrderedByDiscoveryTime() throws InterruptedException {
        DownloadSource source = claimedSource("https://drive.example/order");
        DownloadItem first = downloadItemRepository.save(new DownloadItem(source, "First", "id-1"));
        Thread.sleep(10);
        DownloadItem second = downloadItemRepository.save(new DownloadItem(source, "Second", "id-2"));

        List<DownloadItem> eligible = downloadItemRepository.findEligibleForAutomaticDownload(LocalDateTime.now());

        assertThat(eligible).extracting(DownloadItem::getId)
                .containsExactly(first.getId(), second.getId());
    }
}
