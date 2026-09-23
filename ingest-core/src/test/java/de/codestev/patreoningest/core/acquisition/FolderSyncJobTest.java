package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Import(FolderSyncJobTest.TestConfig.class)
@Transactional
class FolderSyncJobTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private FolderSyncJob folderSyncJob;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    @Autowired
    private FakeDriveFolderListing fakeDriveFolderListing;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeDriveFolderListing fakeDriveFolderListing() {
            return new FakeDriveFolderListing();
        }
    }

    private DownloadSource claimedSource(String url) {
        DownloadSource source = new DownloadSource("nomnom", null, "AUGUST",
                SourceType.DRIVE, url, ClaimType.NONE);
        source.markClaimed();
        return downloadSourceRepository.save(source);
    }

    @Test
    void newTopLevelEntriesBecomePendingDownloadItems() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/folder-1");
        fakeDriveFolderListing.willReturn("folder-1", List.of(
                new DriveEntry("model-a", "Model A", true),
                new DriveEntry("model-b", "Model B", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getModelName, DownloadItem::getRemoteFileId, DownloadItem::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Model A", "model-a", ItemStatus.PENDING),
                        tuple("Model B", "model-b", ItemStatus.PENDING));
    }

    @Test
    void aFileEntryHasItsExtensionStrippedFromTheModelName() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/folder-file");
        fakeDriveFolderListing.willReturn("folder-file", List.of(
                new DriveEntry("file-1", "Stray Model.zip", false)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getModelName)
                .containsExactly("Stray Model");
    }

    @Test
    void reSyncingWithTheSameEntriesDoesNotCreateDuplicates() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/folder-2");
        fakeDriveFolderListing.willReturn("folder-2", List.of(new DriveEntry("model-a", "Model A", true)));

        folderSyncJob.syncAll();
        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId())).hasSize(1);
    }

    @Test
    void zeroNewEntriesOnASyncMarksTheSourceQuiet() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/folder-3");
        fakeDriveFolderListing.willReturn("folder-3", List.of(new DriveEntry("model-a", "Model A", true)));
        folderSyncJob.syncAll();

        fakeDriveFolderListing.willReturn("folder-3", List.of(new DriveEntry("model-a", "Model A", true)));
        folderSyncJob.syncAll();

        DownloadSource reloaded = downloadSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(reloaded.getQuietSince()).isNotNull();
        assertThat(reloaded.getLastSynced()).isNotNull();
    }

    @Test
    void aNewEntryOnALaterSyncClearsTheQuietMarker() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/folder-4");
        fakeDriveFolderListing.willReturn("folder-4", List.of(new DriveEntry("model-a", "Model A", true)));
        folderSyncJob.syncAll();
        folderSyncJob.syncAll();
        assertThat(downloadSourceRepository.findById(source.getId()).orElseThrow().getQuietSince()).isNotNull();

        fakeDriveFolderListing.willReturn("folder-4", List.of(
                new DriveEntry("model-a", "Model A", true),
                new DriveEntry("model-b", "Model B", true)));
        folderSyncJob.syncAll();

        assertThat(downloadSourceRepository.findById(source.getId()).orElseThrow().getQuietSince()).isNull();
    }

    @Test
    void unclaimedSourcesAreNeverSynced() {
        DownloadSource source = new DownloadSource("nomnom", null, "AUGUST",
                SourceType.DRIVE, "https://drive.google.com/drive/folders/folder-5", ClaimType.NONE);
        downloadSourceRepository.save(source);
        fakeDriveFolderListing.willReturn("folder-5", List.of(new DriveEntry("model-a", "Model A", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId())).isEmpty();
        assertThat(downloadSourceRepository.findById(source.getId()).orElseThrow().getLastSynced()).isNull();
    }

    @Test
    void linkDeadSourcesAreNeverSynced() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/folder-6");
        source.markLinkDead();
        downloadSourceRepository.save(source);
        fakeDriveFolderListing.willReturn("folder-6", List.of(new DriveEntry("model-a", "Model A", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId())).isEmpty();
    }

    @Test
    void anUnrecognizedUrlIsFlaggedLinkDeadInsteadOfRetriedForever() {
        // Deterministically unfixable by retrying, unlike a real
        // rclone/network failure - retrying every 30 minutes forever would
        // just spam the same error indefinitely.
        DownloadSource source = claimedSource("https://drive.google.com/nonsense");

        folderSyncJob.syncAll();

        assertThat(downloadSourceRepository.findById(source.getId()).orElseThrow().isLinkDead()).isTrue();

        // A second run must not even attempt it again - it's now excluded
        // from the CLAIMED-and-not-linkDead candidate query entirely.
        folderSyncJob.syncAll();
        assertThat(downloadItemRepository.findBySourceId(source.getId())).isEmpty();
    }

    // Real incident: a parser can legitimately produce a Drive single-file
    // share link ("/file/d/<id>/view") instead of a folder link - this is
    // now a supported, distinct shape, not an error.
    @Test
    void aSingleFileShareUrlRegistersExactlyOneItem() {
        DownloadSource source = claimedSource("https://drive.google.com/file/d/some-file-id/view?usp=sharing");

        folderSyncJob.syncAll();

        assertThat(downloadSourceRepository.findById(source.getId()).orElseThrow().isLinkDead()).isFalse();
        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getRemoteFileId, DownloadItem::getRemoteIsDirectory)
                .containsExactly(tuple("some-file-id", false));
    }

    @Test
    void reSyncingASingleFileShareUrlDoesNotCreateADuplicateItem() {
        DownloadSource source = claimedSource("https://drive.google.com/file/d/some-file-id/view?usp=sharing");

        folderSyncJob.syncAll();
        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId())).hasSize(1);
    }

    @Test
    void oneBadSourceDoesNotStopTheRestOfTheSync() {
        DownloadSource malformed = claimedSource("https://drive.google.com/not-a-folder-url");
        DownloadSource good = claimedSource("https://drive.google.com/drive/folders/folder-7");
        fakeDriveFolderListing.willReturn("folder-7", List.of(new DriveEntry("model-a", "Model A", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(good.getId())).hasSize(1);
        assertThat(downloadItemRepository.findBySourceId(malformed.getId())).isEmpty();
    }
}
