package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Pure decision-logic tests, no real rclone/Drive access needed - that part
// (RcloneProcess actually shelling out) is the one piece of this adapter
// that can't be meaningfully unit tested and is verified manually instead.
class RcloneDriveDownloadAdapterTest {

    private final RcloneDriveDownloadAdapter adapter =
            new RcloneDriveDownloadAdapter(new RcloneProperties("gdrive", "rclone", "/config/rclone.conf"));

    private DownloadSource source(String url) {
        return new DownloadSource("bulkamancer", null, null, SourceType.DRIVE, url, ClaimType.NONE);
    }

    @Test
    void aFolderSyncJobCreatedFolderEntryResolvesAsADirectory() {
        DownloadSource source = source("https://drive.google.com/drive/folders/parent");
        DownloadItem item = new DownloadItem(source, "Model", "the-folder-id", true);

        RcloneDriveDownloadAdapter.DriveReference reference = adapter.resolveReference(item);

        assertThat(reference.id()).isEqualTo("the-folder-id");
        assertThat(reference.isDirectory()).isTrue();
    }

    @Test
    void aFolderSyncJobCreatedFileEntryResolvesAsAFile() {
        DownloadSource source = source("https://drive.google.com/drive/folders/parent");
        DownloadItem item = new DownloadItem(source, "Model", "the-file-id", false);

        RcloneDriveDownloadAdapter.DriveReference reference = adapter.resolveReference(item);

        assertThat(reference.id()).isEqualTo("the-file-id");
        assertThat(reference.isDirectory()).isFalse();
    }

    @Test
    void aLegacyItemWithNoRemoteIsDirectoryFlagDefaultsToDirectory() {
        // Items created before the remote_is_directory column existed -
        // all folders at the time, so this preserves prior behavior.
        DownloadSource source = source("https://drive.google.com/drive/folders/parent");
        DownloadItem item = new DownloadItem(source, "Model", "legacy-id");

        assertThat(adapter.resolveReference(item).isDirectory()).isTrue();
    }

    @Test
    void aDirectlyNamedItemOnAFolderUrlResolvesAsADirectory() {
        DownloadSource source = source("https://drive.google.com/drive/folders/wolverine");
        DownloadItem item = new DownloadItem(source, "Wolverine", null);

        RcloneDriveDownloadAdapter.DriveReference reference = adapter.resolveReference(item);

        assertThat(reference.id()).isEqualTo("wolverine");
        assertThat(reference.isDirectory()).isTrue();
    }

    @Test
    void aDirectlyNamedItemOnASingleFileUrlResolvesAsAFile() {
        DownloadSource source = source("https://drive.google.com/file/d/richter-id/view?usp=sharing");
        DownloadItem item = new DownloadItem(source, "Richter", null);

        RcloneDriveDownloadAdapter.DriveReference reference = adapter.resolveReference(item);

        assertThat(reference.id()).isEqualTo("richter-id");
        assertThat(reference.isDirectory()).isFalse();
    }

    @Test
    void anUnrecognizableUrlThrowsAPermanentFailure() {
        DownloadSource source = source("https://drive.google.com/nonsense");
        DownloadItem item = new DownloadItem(source, "Model", null);

        assertThatThrownBy(() -> adapter.resolveReference(item))
                .isInstanceOf(DownloadFailedException.class)
                .satisfies(e -> assertThat(((DownloadFailedException) e).isPermanent()).isTrue());
    }
}
