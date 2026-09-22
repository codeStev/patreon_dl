package de.codestev.patreoningest.core.acquisition;

import java.util.List;

// Out-port for FolderSyncJob - lists the top-level entries of a Drive
// folder so new models/files can be diffed against known remote_file_ids.
// Implemented by RcloneDriveFolderListingAdapter (core/fulfillment), which
// shells out to `rclone lsjson`. Defined here, next to its consumer, same
// rule as ClaimPort living next to ClaimSourceUseCase.
public interface DriveFolderListing {

    List<DriveEntry> list(String folderId);
}
