package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.SourceType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

// Test double shared across the Fulfillment use-case tests - avoids
// shelling out to real rclone. The real RcloneDriveDownloadAdapter is
// neutralized per-test via @MockitoBean (an unstubbed mock's supports()
// returns null, so it's filtered out of the List<SourceDownloader>
// dispatch), leaving this as the only DRIVE downloader in the context.
class FakeSourceDownloader implements SourceDownloader {

    private volatile DownloadResult nextResult;
    private volatile DownloadFailedException nextException;
    private volatile long delayMillis;
    private volatile boolean createSpacedFilesUnderTargetDir;
    private final AtomicInteger callCount = new AtomicInteger();

    @Override
    public SourceType supports() {
        return SourceType.DRIVE;
    }

    @Override
    public DownloadResult fetch(DownloadItem item, Path targetDir, DownloadRuntimeOptions options) {
        callCount.incrementAndGet();
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (nextException != null) {
            throw nextException;
        }
        if (createSpacedFilesUnderTargetDir) {
            return createSpacedFilesUnder(targetDir);
        }
        return nextResult;
    }

    private static DownloadResult createSpacedFilesUnder(Path targetDir) {
        try {
            Path nested = targetDir.resolve("nested folder");
            Files.createDirectories(nested);
            Files.writeString(targetDir.resolve("top level.txt"), "x");
            Files.writeString(nested.resolve("deep file.txt"), "xx");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new DownloadResult(targetDir.toString(), 3L);
    }

    // Keeps a dispatched task "in flight" long enough for a test to observe
    // the concurrency tracker's state before it's released.
    void withDelay(long millis) {
        this.delayMillis = millis;
    }

    void willSucceedWith(DownloadResult result) {
        this.nextResult = result;
        this.nextException = null;
    }

    void willFailWith(DownloadFailedException exception) {
        this.nextException = exception;
        this.nextResult = null;
    }

    void willSucceedByCreatingSpacedFilesUnderTargetDir() {
        this.createSpacedFilesUnderTargetDir = true;
        this.nextException = null;
    }

    int callCount() {
        return callCount.get();
    }

    // The @TestConfiguration bean instance is shared across every test
    // method in a class (Spring caches the context) - reset in @AfterEach
    // wherever a test asserts on callCount().
    void reset() {
        callCount.set(0);
        delayMillis = 0;
        nextResult = null;
        nextException = null;
        createSpacedFilesUnderTargetDir = false;
    }
}
