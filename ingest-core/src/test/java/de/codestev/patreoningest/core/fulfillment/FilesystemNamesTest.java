package de.codestev.patreoningest.core.fulfillment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FilesystemNamesTest {

    @Test
    void sanitizeReplacesFilesystemUnsafeCharacters() {
        assertThat(FilesystemNames.sanitize("Scale 1/4 (mm)")).isEqualTo("Scale 1_4 (mm)");
        assertThat(FilesystemNames.sanitize("normal name")).isEqualTo("normal name");
    }

    @Test
    void sanitizeTruncatesAnUnexpectedlyLongName() {
        String result = FilesystemNames.sanitize("x".repeat(300));

        assertThat(result).hasSize(100);
    }

    @Test
    void recursiveRenameReplacesSpacesInTheRootAndEveryDescendant(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("Cool Dragon");
        Path nestedDir = root.resolve("sub folder");
        Files.createDirectories(nestedDir);
        Files.createFile(root.resolve("top level.zip"));
        Files.createFile(nestedDir.resolve("deep file.png"));

        Path newRoot = FilesystemNames.renameSpacesToUnderscoresRecursively(root);

        assertThat(newRoot.getFileName().toString()).isEqualTo("Cool_Dragon");
        assertThat(Files.exists(newRoot)).isTrue();
        assertThat(Files.exists(newRoot.resolve("top_level.zip"))).isTrue();
        assertThat(Files.exists(newRoot.resolve("sub_folder"))).isTrue();
        assertThat(Files.exists(newRoot.resolve("sub_folder/deep_file.png"))).isTrue();
    }

    @Test
    void recursiveRenameIsANoOpWhenNothingHasASpace(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("NoSpaces");
        Files.createDirectories(root);
        Files.createFile(root.resolve("file.txt"));

        Path result = FilesystemNames.renameSpacesToUnderscoresRecursively(root);

        assertThat(result).isEqualTo(root);
        assertThat(Files.exists(root.resolve("file.txt"))).isTrue();
    }

    @Test
    void recursiveRenameOnAMissingPathIsANoOp(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does not exist");

        Path result = FilesystemNames.renameSpacesToUnderscoresRecursively(missing);

        assertThat(result).isEqualTo(missing);
    }
}
