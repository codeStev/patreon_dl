package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DriveEntry;
import de.codestev.patreoningest.core.acquisition.DriveFolderListing;
import de.codestev.patreoningest.core.acquisition.NestedDriveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class RcloneDriveFolderListingAdapter implements DriveFolderListing {

    private static final Logger log = LoggerFactory.getLogger(RcloneDriveFolderListingAdapter.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RcloneProperties properties;

    public RcloneDriveFolderListingAdapter(RcloneProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<DriveEntry> list(String folderId) {
        List<DriveEntry> entries = parseEntries(runLsjson(folderId, List.of()));
        log.debug("Listed {} top-level entr{} for folder {}", entries.size(),
                entries.size() == 1 ? "y" : "ies", folderId);
        return entries;
    }

    // One lsjson call for both levels rather than one per top-level folder
    // - a persistent link can hold dozens of creator folders, and each
    // extra call is another Drive API round trip on every sync.
    @Override
    public List<NestedDriveEntry> listTwoLevels(String folderId) {
        List<NestedDriveEntry> entries = parseNestedEntries(runLsjson(folderId, List.of("--max-depth", "2")));
        log.debug("Listed {} entr{} two levels deep for folder {}", entries.size(),
                entries.size() == 1 ? "y" : "ies", folderId);
        return entries;
    }

    private String runLsjson(String folderId, List<String> extraArgs) {
        List<String> command = new ArrayList<>(List.of(
                properties.binaryPath(), "--config=" + properties.configPath(), "lsjson",
                "--drive-root-folder-id=" + folderId));
        command.addAll(extraArgs);
        command.add(properties.remoteName() + ":");

        RcloneProcess.Result result;
        try {
            result = RcloneProcess.run(command, TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while listing folder " + folderId, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run rclone lsjson for folder " + folderId, e);
        }

        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "rclone lsjson failed for folder " + folderId + " (exit " + result.exitCode() + "): "
                            + result.stderr().trim());
        }
        return result.stdout();
    }

    static List<DriveEntry> parseEntries(String json) {
        List<DriveEntry> entries = new ArrayList<>();
        for (JsonNode entry : OBJECT_MAPPER.readTree(json)) {
            entries.add(toDriveEntry(entry));
        }
        return entries;
    }

    // "Path" is "A" at the top level and "A/B" one level down. rclone
    // encodes a "/" inside a Drive name as a lookalike character, so the
    // first "/" always separates the two levels.
    static List<NestedDriveEntry> parseNestedEntries(String json) {
        List<NestedDriveEntry> entries = new ArrayList<>();
        for (JsonNode entry : OBJECT_MAPPER.readTree(json)) {
            String path = entry.path("Path").asString();
            int slash = path.indexOf('/');
            String parentName = slash < 0 ? null : path.substring(0, slash);
            entries.add(new NestedDriveEntry(parentName, toDriveEntry(entry)));
        }
        return entries;
    }

    private static DriveEntry toDriveEntry(JsonNode entry) {
        return new DriveEntry(
                entry.path("ID").asString(),
                entry.path("Name").asString(),
                entry.path("IsDir").asBoolean());
    }
}
