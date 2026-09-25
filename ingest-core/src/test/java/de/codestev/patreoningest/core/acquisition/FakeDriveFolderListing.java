package de.codestev.patreoningest.core.acquisition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Test double for FolderSyncJobTest - avoids shelling out to real rclone.
// Registered with @Primary in the test's @TestConfiguration since
// DriveFolderListing is a single-bean injection point (unlike
// SourceDownloader, which needs the @MockitoBean neutralization trick for
// its List injection point).
class FakeDriveFolderListing implements DriveFolderListing {

    private final Map<String, List<DriveEntry>> entriesByFolderId = new ConcurrentHashMap<>();
    private final Map<String, List<NestedDriveEntry>> nestedEntriesByFolderId = new ConcurrentHashMap<>();

    @Override
    public List<DriveEntry> list(String folderId) {
        return entriesByFolderId.getOrDefault(folderId, List.of());
    }

    @Override
    public List<NestedDriveEntry> listTwoLevels(String folderId) {
        return nestedEntriesByFolderId.getOrDefault(folderId, List.of());
    }

    void willReturn(String folderId, List<DriveEntry> entries) {
        entriesByFolderId.put(folderId, entries);
    }

    void willReturnTwoLevels(String folderId, List<NestedDriveEntry> entries) {
        nestedEntriesByFolderId.put(folderId, entries);
    }
}
