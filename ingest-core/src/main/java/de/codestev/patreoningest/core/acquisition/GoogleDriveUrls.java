package de.codestev.patreoningest.core.acquisition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Shared by FolderSyncJob (Acquisition) and the rclone adapters
// (Fulfillment) - both need to turn a download_source.sourceUrl like
// https://drive.google.com/drive/folders/<id>?patron=... into the bare
// folder id rclone's --drive-root-folder-id expects.
public final class GoogleDriveUrls {

    private static final Pattern FOLDER_ID = Pattern.compile("/folders/([a-zA-Z0-9_-]+)");

    private GoogleDriveUrls() {
    }

    public static String extractFolderId(String driveUrl) {
        Matcher matcher = FOLDER_ID.matcher(driveUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a recognizable Google Drive folder URL: " + driveUrl);
        }
        return matcher.group(1);
    }
}
