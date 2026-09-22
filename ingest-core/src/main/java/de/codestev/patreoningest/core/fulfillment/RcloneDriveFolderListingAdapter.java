package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DriveEntry;
import de.codestev.patreoningest.core.acquisition.DriveFolderListing;
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

    private final RcloneProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RcloneDriveFolderListingAdapter(RcloneProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<DriveEntry> list(String folderId) {
        List<String> command = List.of(
                properties.binaryPath(), "--config=" + properties.configPath(), "lsjson",
                "--drive-root-folder-id=" + folderId,
                properties.remoteName() + ":");

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

        List<DriveEntry> entries = parseEntries(result.stdout());
        log.debug("Listed {} top-level entr{} for folder {}", entries.size(),
                entries.size() == 1 ? "y" : "ies", folderId);
        return entries;
    }

    private List<DriveEntry> parseEntries(String json) {
        JsonNode root = objectMapper.readTree(json);
        List<DriveEntry> entries = new ArrayList<>();
        for (JsonNode entry : root) {
            entries.add(new DriveEntry(
                    entry.path("ID").asString(),
                    entry.path("Name").asString(),
                    entry.path("IsDir").asBoolean()));
        }
        return entries;
    }
}
