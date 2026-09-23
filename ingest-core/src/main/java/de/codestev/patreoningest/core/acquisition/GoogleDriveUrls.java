package de.codestev.patreoningest.core.acquisition;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Shared by FolderSyncJob (Acquisition) and the rclone adapters
// (Fulfillment) - both need to turn a download_source.sourceUrl into the
// bare Drive object ID rclone needs. Real production data surfaced both
// shapes: "/drive/folders/<id>" (the common case - a shared folder) and
// "/file/d/<id>/view" (a single shared file, no folder at all).
public final class GoogleDriveUrls {

    private static final Pattern FOLDER_ID = Pattern.compile("/folders/([a-zA-Z0-9_-]+)");
    private static final Pattern FILE_ID = Pattern.compile("/file/d/([a-zA-Z0-9_-]+)");

    private GoogleDriveUrls() {
    }

    public static Optional<String> tryExtractFolderId(String driveUrl) {
        Matcher matcher = FOLDER_ID.matcher(driveUrl);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public static Optional<String> tryExtractFileId(String driveUrl) {
        Matcher matcher = FILE_ID.matcher(driveUrl);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
