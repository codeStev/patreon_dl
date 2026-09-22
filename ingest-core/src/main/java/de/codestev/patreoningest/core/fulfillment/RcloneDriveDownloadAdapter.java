package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.GoogleDriveUrls;
import de.codestev.patreoningest.core.acquisition.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class RcloneDriveDownloadAdapter implements SourceDownloader {

    private static final Logger log = LoggerFactory.getLogger(RcloneDriveDownloadAdapter.class);
    private static final Duration TIMEOUT = Duration.ofHours(2);
    // rclone's own documented exit codes: 3 = directory not found, 4 = file
    // not found, 7 = fatal error (e.g. account suspended) - a real signal
    // the source is dead, not worth retrying. Everything else (including
    // exit 5, rclone's own "temporary error" code) is treated as transient
    // and goes through the retry/backoff schedule instead.
    private static final Set<Integer> PERMANENT_EXIT_CODES = Set.of(3, 4, 7);

    private final RcloneProperties properties;

    public RcloneDriveDownloadAdapter(RcloneProperties properties) {
        this.properties = properties;
    }

    @Override
    public SourceType supports() {
        return SourceType.DRIVE;
    }

    @Override
    public DownloadResult fetch(DownloadItem item, Path targetDir, DownloadRuntimeOptions options) {
        String folderId = item.getRemoteFileId() != null
                ? item.getRemoteFileId()
                : GoogleDriveUrls.extractFolderId(item.getSource().getSourceUrl());

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            // Include the real cause's message - "Could not create target
            // directory X" alone hid whether it was a permissions issue,
            // a full disk, a read-only filesystem, or something else
            // entirely, forcing a guessing game in the logs.
            throw new DownloadFailedException(
                    "Could not create target directory " + targetDir + ": " + e.getMessage(), false, e);
        }

        List<String> command = buildCommand(folderId, targetDir, options);
        log.info("Fetching item {} via rclone: {}", item.getId(), command);

        RcloneProcess.Result result = runRclone(item, command);

        if (result.exitCode() != 0) {
            boolean permanent = PERMANENT_EXIT_CODES.contains(result.exitCode());
            throw new DownloadFailedException(
                    "rclone copy failed (exit " + result.exitCode() + "): " + result.stderr().trim(),
                    permanent);
        }

        return new DownloadResult(targetDir.toString(), directorySize(targetDir));
    }

    private List<String> buildCommand(String folderId, Path targetDir, DownloadRuntimeOptions options) {
        List<String> command = new ArrayList<>();
        if (options.ioNice()) {
            command.add("ionice");
            command.add("-c3");
        }
        command.add(properties.binaryPath());
        command.add("--config=" + properties.configPath());
        command.add("copy");
        command.add("--drive-root-folder-id=" + folderId);
        // Always-on Drive rate-limit safety, independent of and in addition
        // to the app-level max_concurrent_downloads setting - never let a
        // single rclone invocation's own internal parallelism hammer the
        // API just because the queue-level concurrency cap is satisfied.
        // Hardcoded, not user-configurable.
        command.add("--transfers=1");
        command.add("--checkers=1");
        command.add("--drive-pacer-min-sleep=100ms");
        if (options.bandwidthLimitKbps() != null) {
            command.add("--bwlimit=" + options.bandwidthLimitKbps() + "k");
        }
        command.add(properties.remoteName() + ":");
        command.add(targetDir.toString());
        return command;
    }

    private RcloneProcess.Result runRclone(DownloadItem item, List<String> command) {
        try {
            return RcloneProcess.run(command, TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownloadFailedException("Interrupted while downloading item " + item.getId(), false, e);
        } catch (IOException e) {
            throw new DownloadFailedException("Failed to run rclone for item " + item.getId(), false, e);
        }
    }

    private static long directorySize(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(RcloneDriveDownloadAdapter::sizeOrZero)
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long sizeOrZero(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }
}
