package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.ClaimStatus;
import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.ItemStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

// Explicit user override of automatic provider policy (an item stuck on
// MANUAL, or a FAILED item, can be forced right now) - but NOT an override
// of the concurrency cap, which stays the single choke point shared with
// RunDownloadQueueTickUseCase. Also skips the allowed-hours window
// deliberately: a real-time explicit click overrides a passive schedule
// guard, unlike the automatic tick.
@Component
public class TriggerManualDownloadUseCase {

    public enum Result {
        DISPATCHED, NOT_FOUND, ALREADY_DOWNLOADED, NOT_CLAIMED, LINK_DEAD, CONCURRENCY_LIMIT_REACHED
    }

    private final DownloadItemRepository downloadItemRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final DownloadConcurrencyTracker concurrencyTracker;
    private final ExecuteDownloadUseCase executeDownloadUseCase;
    private final ExecutorService downloadExecutor;

    public TriggerManualDownloadUseCase(DownloadItemRepository downloadItemRepository,
                                         AppSettingsRepository appSettingsRepository,
                                         DownloadConcurrencyTracker concurrencyTracker,
                                         ExecuteDownloadUseCase executeDownloadUseCase,
                                         ExecutorService downloadExecutor) {
        this.downloadItemRepository = downloadItemRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.concurrencyTracker = concurrencyTracker;
        this.executeDownloadUseCase = executeDownloadUseCase;
        this.downloadExecutor = downloadExecutor;
    }

    @Transactional
    public Result trigger(UUID itemId) {
        Optional<DownloadItem> maybeItem = downloadItemRepository.findById(itemId);
        if (maybeItem.isEmpty()) {
            return Result.NOT_FOUND;
        }
        DownloadItem item = maybeItem.get();

        if (item.getStatus() == ItemStatus.DOWNLOADED) {
            return Result.ALREADY_DOWNLOADED;
        }
        if (item.getSource().getClaimStatus() != ClaimStatus.CLAIMED) {
            return Result.NOT_CLAIMED;
        }
        if (item.getSource().isLinkDead()) {
            return Result.LINK_DEAD;
        }

        if (item.getStatus() == ItemStatus.FAILED) {
            item.resetForManualRetry();
        }
        downloadItemRepository.save(item);

        AppSettings settings = appSettingsRepository.findById(1L).orElseThrow();
        if (!concurrencyTracker.tryAcquire(settings.getMaxConcurrentDownloads())) {
            return Result.CONCURRENCY_LIMIT_REACHED;
        }

        UUID id = item.getId();
        downloadExecutor.submit(() -> {
            try {
                executeDownloadUseCase.execute(id);
            } finally {
                concurrencyTracker.release();
            }
        });
        return Result.DISPATCHED;
    }
}
