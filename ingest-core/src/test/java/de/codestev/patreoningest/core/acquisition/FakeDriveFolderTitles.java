package de.codestev.patreoningest.core.acquisition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Test double for FolderSyncJobTest - no network. Unknown folders have no title.
class FakeDriveFolderTitles implements DriveFolderTitles {

    private final Map<String, String> titles = new HashMap<>();

    void willReturn(String folderId, String title) {
        titles.put(folderId, title);
    }

    @Override
    public Optional<String> titleOf(String folderId) {
        return Optional.ofNullable(titles.get(folderId));
    }
}
