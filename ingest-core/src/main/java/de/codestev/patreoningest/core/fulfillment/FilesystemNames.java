package de.codestev.patreoningest.core.fulfillment;

// Model names come straight from parsed email text and can contain
// filesystem-unsafe characters - e.g. a real Wicked sample has a model
// named "... Scale 1/4 (mm)", where the "/" would otherwise be read as a
// path separator.
final class FilesystemNames {

    private FilesystemNames() {
    }

    static String sanitize(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
