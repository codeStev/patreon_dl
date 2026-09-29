package de.codestev.patreoningest.core.acquisition;

import java.util.Optional;

// Out-port for FolderSyncJob - the real name of a shared Drive folder
// (e.g. "Chibi He-Man"), which rclone can't report for a folder addressed
// by ID (see FolderSyncJob.fallbackModelLabel). Empty when it can't be
// found out; callers then use a generic label.
// Implemented by HttpDriveFolderTitles (core/fulfillment).
public interface DriveFolderTitles {

    Optional<String> titleOf(String folderId);
}
