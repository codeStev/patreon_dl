package de.codestev.patreoningest.core.acquisition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleDriveUrlsTest {

    @Test
    void extractsAFolderIdFromAFolderUrl() {
        assertThat(GoogleDriveUrls.tryExtractFolderId(
                "https://drive.google.com/drive/folders/1Q199aX_UUsNpXr4u9qdea5evnA3gNBS4?patron=38db"))
                .contains("1Q199aX_UUsNpXr4u9qdea5evnA3gNBS4");
    }

    @Test
    void extractsAFileIdFromASingleFileShareUrl() {
        assertThat(GoogleDriveUrls.tryExtractFileId(
                "https://drive.google.com/file/d/1w_XLKRC4feMTWIR6oINJkAUOJHihEdg5/view?usp=sharing"))
                .contains("1w_XLKRC4feMTWIR6oINJkAUOJHihEdg5");
    }

    @Test
    void aFolderUrlHasNoFileId() {
        assertThat(GoogleDriveUrls.tryExtractFileId(
                "https://drive.google.com/drive/folders/some-folder")).isEmpty();
    }

    @Test
    void aFileUrlHasNoFolderId() {
        assertThat(GoogleDriveUrls.tryExtractFolderId(
                "https://drive.google.com/file/d/some-file/view")).isEmpty();
    }

    @Test
    void anUnrecognizedUrlMatchesNeither() {
        assertThat(GoogleDriveUrls.tryExtractFolderId("https://drive.google.com/nonsense")).isEmpty();
        assertThat(GoogleDriveUrls.tryExtractFileId("https://drive.google.com/nonsense")).isEmpty();
    }
}
