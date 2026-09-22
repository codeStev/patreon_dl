package de.codestev.patreoningest.core.fulfillment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

// Model names come straight from parsed email text and can contain
// filesystem-unsafe characters - e.g. a real Wicked sample has a model
// named "... Scale 1/4 (mm)", where the "/" would otherwise be read as a
// path separator.
final class FilesystemNames {

    private static final Logger log = LoggerFactory.getLogger(FilesystemNames.class);

    // Belt-and-braces against ENAMETOOLONG (Linux's ~255-byte NAME_MAX per
    // path segment) - a real incident had a malformed email leak raw HTML
    // into a "model name" via BulkamancerParser, which is now rejected at
    // the parser level, but this also protects names sourced directly from
    // real Drive file/folder names (FolderSyncJob), which no parser
    // validates. 100 is comfortably under the byte limit even accounting
    // for multi-byte UTF-8 characters in a typical name.
    private static final int MAX_NAME_LENGTH = 100;

    private FilesystemNames() {
    }

    static String sanitize(String name) {
        String cleaned = name.replaceAll("[/\\\\:*?\"<>|]", "_");
        if (cleaned.length() > MAX_NAME_LENGTH) {
            log.warn("Truncating unexpectedly long name ({} chars) before using it as a directory name: {}",
                    cleaned.length(), cleaned.substring(0, 40));
            return cleaned.substring(0, MAX_NAME_LENGTH);
        }
        return cleaned;
    }

    // Optional post-download normalization (AppSettings.renameSpacesToUnderscores):
    // recursively replaces spaces with underscores in every file/folder name
    // under root, including root itself. Processes deepest paths first so
    // renaming a directory never invalidates the still-to-be-processed path
    // of anything inside it (a rename only ever touches a path's own last
    // segment, never its ancestors). Returns root's own possibly-new path.
    static Path renameSpacesToUnderscoresRecursively(Path root) {
        if (!Files.exists(root)) {
            return root;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> descendants = walk
                    .filter(path -> !path.equals(root))
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .toList();
            for (Path path : descendants) {
                renameIfNeeded(path);
            }
        } catch (IOException e) {
            log.warn("Failed to walk {} for space-to-underscore renaming - leaving names as-is", root, e);
            return root;
        }
        return renameIfNeeded(root);
    }

    private static Path renameIfNeeded(Path path) {
        String name = path.getFileName().toString();
        if (!name.contains(" ")) {
            return path;
        }
        Path renamed = path.resolveSibling(name.replace(' ', '_'));
        try {
            return Files.move(path, renamed);
        } catch (IOException e) {
            log.warn("Failed to rename {} to {} - leaving original name", path, renamed, e);
            return path;
        }
    }
}
