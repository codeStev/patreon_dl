package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Runs one item through its matching SourceDownloader and applies the
// resulting state transition. Used by both the scheduled queue tick and a
// manual "download now" trigger - all the retry/backoff/permanent-failure
// logic lives in exactly one place regardless of who dispatched it.
@Component
public class ExecuteDownloadUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecuteDownloadUseCase.class);

    // 1m, 5m, 30m, 30m, 30m - matches the design doc's retry/backoff spec.
    // Capped at 5 attempts, then the item is left FAILED rather than
    // retried forever against a possibly-dead source.
    private static final int[] BACKOFF_MINUTES = {1, 5, 30, 30, 30};
    static final int MAX_ATTEMPTS = BACKOFF_MINUTES.length;

    private final DownloadItemRepository downloadItemRepository;
    private final DownloadSourceRepository downloadSourceRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final List<SourceDownloader> downloaders;
    private final FulfillmentProperties properties;

    public ExecuteDownloadUseCase(DownloadItemRepository downloadItemRepository,
                                   DownloadSourceRepository downloadSourceRepository,
                                   AppSettingsRepository appSettingsRepository,
                                   List<SourceDownloader> downloaders,
                                   FulfillmentProperties properties) {
        this.downloadItemRepository = downloadItemRepository;
        this.downloadSourceRepository = downloadSourceRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.downloaders = downloaders;
        this.properties = properties;
    }

    @Transactional
    public void execute(UUID itemId) {
        DownloadItem item = downloadItemRepository.findById(itemId).orElse(null);
        if (item == null) {
            log.warn("Download item {} no longer exists - skipping", itemId);
            return;
        }

        try {
            runDownload(item);
        } catch (Exception e) {
            // A genuinely unexpected bug (not a DownloadFailedException from
            // the adapter itself) - deliberately left PENDING/unchanged
            // rather than guessed at as transient or permanent. Loud log
            // for a human; the next tick will retry it, which is
            // acceptable since this path is not expected to trigger under
            // normal operation.
            log.error("Unexpected error executing download for item {}", itemId, e);
        }
    }

    private void runDownload(DownloadItem item) {
        Optional<SourceDownloader> downloader = downloaders.stream()
                .filter(d -> d.supports() == item.getSource().getSourceType())
                .findFirst();
        if (downloader.isEmpty()) {
            log.warn("No SourceDownloader registered for source type {} (item {}) - skipping",
                    item.getSource().getSourceType(), item.getId());
            return;
        }

        AppSettings settings = appSettingsRepository.findById(1L).orElseThrow();
        DownloadRuntimeOptions options = new DownloadRuntimeOptions(
                settings.getBandwidthLimitKbps(), settings.isIoNice());
        Path targetDir = Path.of(properties.downloadRoot(),
                item.getSource().getCreator(), FilesystemNames.sanitize(item.getModelName()));

        try {
            DownloadResult result = downloader.get().fetch(item, targetDir, options);
            String localPath = result.localPath();
            if (settings.isRenameSpacesToUnderscores()) {
                // Renamed before the item is marked DOWNLOADED, since the
                // user moves folders out of downloadRoot afterward - this
                // is the only point where the app still controls the path.
                localPath = FilesystemNames.renameSpacesToUnderscoresRecursively(Path.of(localPath)).toString();
            }
            item.markDownloaded(localPath, result.fileSizeBytes());
            downloadItemRepository.save(item);
            log.info("Downloaded item {} ({} bytes) to {}", item.getId(), result.fileSizeBytes(), result.localPath());
        } catch (DownloadFailedException e) {
            handleFailure(item, e);
        }
    }

    private void handleFailure(DownloadItem item, DownloadFailedException e) {
        if (e.isPermanent()) {
            item.markFailedPermanently(e.getMessage());
            item.getSource().markLinkDead();
            downloadSourceRepository.save(item.getSource());
            log.error("Item {} failed permanently - flagging source {} link-dead: {}",
                    item.getId(), item.getSource().getId(), e.getMessage(), e);
        } else if (item.getRetryCount() >= MAX_ATTEMPTS) {
            item.markFailedPermanently(e.getMessage());
            log.error("Item {} exceeded {} retry attempts, giving up: {}",
                    item.getId(), MAX_ATTEMPTS, e.getMessage(), e);
        } else {
            LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(BACKOFF_MINUTES[item.getRetryCount()]);
            item.recordTransientFailure(e.getMessage(), nextAttempt);
            // Trailing `e` (beyond the placeholders) makes SLF4J print the
            // full stack trace too - a bare e.getMessage() string previously
            // hid the actual cause (e.g. the real IOException reason) from
            // anyone reading the logs.
            log.warn("Item {} failed transiently (attempt {}/{}), retrying at {}: {}",
                    item.getId(), item.getRetryCount(), MAX_ATTEMPTS, nextAttempt, e.getMessage(), e);
        }
        downloadItemRepository.save(item);
    }
}
