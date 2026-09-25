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

    // Real observed data: a Nomnom link can point directly at one model's
    // own folder (e.g. "Chibi He-Man") containing only organizational
    // subfolders ("STL"/"Render Images"/"Presupport"), not separate models.
    @Test
    void aFolderContainingOnlyOrganizationalSubfoldersIsRegisteredAsOneModel() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/chibi-he-man");
        fakeDriveFolderListing.willReturn("chibi-he-man", List.of(
                new DriveEntry("stl-id", "STL", true),
                new DriveEntry("renders-id", "Render Images", true),
                new DriveEntry("presupport-id", "Presupport", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getRemoteFileId, DownloadItem::getRemoteIsDirectory)
                .containsExactly(tuple("chibi-he-man", true));
    }

    @Test
    void aFolderWithAMixOfOrganizationalAndRealModelNamesIsStillEnumerated() {
        // Only ALL-organizational triggers the single-model treatment - a
        // genuine multi-model folder's names won't coincidentally collide
        // with the whole vocabulary, but a partial/ambiguous match should
        // fail safe toward the existing per-entry behavior.
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/mixed");
        fakeDriveFolderListing.willReturn("mixed", List.of(
                new DriveEntry("stl-id", "STL", true),
                new DriveEntry("model-id", "Cool Dragon", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getModelName)
                .containsExactlyInAnyOrder("STL", "Cool Dragon");
    }

    @Test
    void reSyncingASingleModelFolderDoesNotCreateADuplicateItem() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/chibi-he-man");
        fakeDriveFolderListing.willReturn("chibi-he-man", List.of(
                new DriveEntry("stl-id", "STL", true),
                new DriveEntry("presupport-id", "Presupport", true)));

        folderSyncJob.syncAll();
        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId())).hasSize(1);
    }

    // Real incident: a Bulkamancer standalone-DM link ("Wolverine: <folder-url>")
    // is registered by BulkamancerParser as one directly-named item covering
    // the WHOLE folder (a clean 1:1 mapping, downloaded recursively as one
    // unit). That folder's own contents can still include variant
    // subfolders ("wolverine_no_supports", "wolverine_uncut") and a Readme
    // alongside them - before this fix, FolderSyncJob independently listed
    // that same folder and, since not every entry matched the
    // organizational vocabulary, enumerated each one as its own spurious
    // top-level "model" duplicate of content the named item already covers.
    @Test
    void aSourceWithAnExistingDirectlyNamedItemIsNeverEnumerated() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/wolverine-folder");
        downloadItemRepository.save(new DownloadItem(source, "Wolverine", null));
        fakeDriveFolderListing.willReturn("wolverine-folder", List.of(
                new DriveEntry("readme-id", "Readme", false),
                new DriveEntry("no-supports-id", "wolverine_no_supports", true),
                new DriveEntry("uncut-id", "wolverine_uncut", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getModelName)
                .containsExactly("Wolverine");
    }

    // Verifies the actual danger the fallback-naming fix addresses: two
    // different single-model folders (both generic, no month/category to
    // fall back to) must never resolve to the identical label, or their
    // downloads would land in the same directory and merge together.
    @Test
    void twoDifferentFallbackLabeledFoldersDoNotCollide() {
        DownloadSource chibiHeMan = claimedSource("https://drive.google.com/drive/folders/chibi-he-man-id");
        fakeDriveFolderListing.willReturn("chibi-he-man-id", List.of(new DriveEntry("s", "STL", true)));
        DownloadSource skeletor = claimedSource("https://drive.google.com/drive/folders/skeletor-id");
        fakeDriveFolderListing.willReturn("skeletor-id", List.of(new DriveEntry("s", "STL", true)));

        folderSyncJob.syncAll();

        String chibiLabel = downloadItemRepository.findBySourceId(chibiHeMan.getId()).get(0).getModelName();
        String skeletorLabel = downloadItemRepository.findBySourceId(skeletor.getId()).get(0).getModelName();
        assertThat(chibiLabel).isNotEqualTo(skeletorLabel);
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

    private DownloadSource claimedCollectionsSource(String folderId) {
        DownloadSource source = new DownloadSource("my-archive", null, null, SourceType.DRIVE,
                "https://drive.google.com/drive/folders/" + folderId, ClaimType.NONE, FolderLayout.COLLECTIONS);
        source.markClaimed();
        return downloadSourceRepository.save(source);
    }

    private static NestedDriveEntry top(String id, String name, boolean isDirectory) {
        return new NestedDriveEntry(null, new DriveEntry(id, name, isDirectory));
    }

    private static NestedDriveEntry child(String parent, String id, String name, boolean isDirectory) {
        return new NestedDriveEntry(parent, new DriveEntry(id, name, isDirectory));
    }

    @Test
    void eachCollectionInACreatorFolderBecomesOneGroupedItem() {
        DownloadSource source = claimedCollectionsSource("rolling-1");
        fakeDriveFolderListing.willReturnTwoLevels("rolling-1", List.of(
                top("titan", "Titan Forge", true),
                child("Titan Forge", "titan-jan", "2025-01 Release", true),
                child("Titan Forge", "titan-feb", "2025-02 Release", true),
                top("loot", "Loot Studios", true),
                child("Loot Studios", "loot-bonus", "Bonus Pack.zip", false)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getGroupName, DownloadItem::getModelName,
                        DownloadItem::getRemoteFileId, DownloadItem::getRemoteIsDirectory)
                .containsExactlyInAnyOrder(
                        tuple("Titan Forge", "2025-01 Release", "titan-jan", true),
                        tuple("Titan Forge", "2025-02 Release", "titan-feb", true),
                        tuple("Loot Studios", "Bonus Pack", "loot-bonus", false));
    }

    @Test
    void aNewCollectionOnALaterSyncIsRegisteredWithoutDuplicatingTheOthers() {
        DownloadSource source = claimedCollectionsSource("rolling-2");
        fakeDriveFolderListing.willReturnTwoLevels("rolling-2", List.of(
                top("titan", "Titan Forge", true),
                child("Titan Forge", "titan-jan", "2025-01 Release", true)));
        folderSyncJob.syncAll();
        folderSyncJob.syncAll();
        assertThat(downloadSourceRepository.findById(source.getId()).orElseThrow().getQuietSince()).isNotNull();

        fakeDriveFolderListing.willReturnTwoLevels("rolling-2", List.of(
                top("titan", "Titan Forge", true),
                child("Titan Forge", "titan-jan", "2025-01 Release", true),
                child("Titan Forge", "titan-feb", "2025-02 Release", true)));
        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getRemoteFileId)
                .containsExactlyInAnyOrder("titan-jan", "titan-feb");
        assertThat(downloadSourceRepository.findById(source.getId()).orElseThrow().getQuietSince()).isNull();
    }

    @Test
    void aTopLevelFolderThatIsOneModelsOwnFolderBecomesOneUngroupedItem() {
        DownloadSource source = claimedCollectionsSource("rolling-3");
        fakeDriveFolderListing.willReturnTwoLevels("rolling-3", List.of(
                top("dragon", "Big Dragon", true),
                child("Big Dragon", "dragon-stl", "STL", true),
                child("Big Dragon", "dragon-pre", "Presupported", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getGroupName, DownloadItem::getModelName, DownloadItem::getRemoteFileId)
                .containsExactly(tuple(null, "Big Dragon", "dragon"));
    }

    @Test
    void aTopLevelFileBecomesOneUngroupedItemAndAnEmptyFolderNothingYet() {
        DownloadSource source = claimedCollectionsSource("rolling-4");
        fakeDriveFolderListing.willReturnTwoLevels("rolling-4", List.of(
                top("readme", "Read me first.pdf", false),
                top("empty", "Coming Soon", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getGroupName, DownloadItem::getModelName, DownloadItem::getRemoteIsDirectory)
                .containsExactly(tuple(null, "Read me first", false));
    }

    @Test
    void aModelsSourceNeverUsesTheTwoLevelListing() {
        DownloadSource source = claimedSource("https://drive.google.com/drive/folders/flat-1");
        fakeDriveFolderListing.willReturn("flat-1", List.of(new DriveEntry("model-a", "Model A", true)));
        fakeDriveFolderListing.willReturnTwoLevels("flat-1", List.of(
                child("Model A", "part", "Part", true)));

        folderSyncJob.syncAll();

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getGroupName, DownloadItem::getRemoteFileId)
                .containsExactly(tuple(null, "model-a"));
    }
}
