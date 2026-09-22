package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadPolicy;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.ProviderSettings;
import de.codestev.patreoningest.core.acquisition.ProviderSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

// Single global choke point (see the design doc's "Performance/I/O
// constraints") - the only place PENDING items get automatically
// dispatched. Manual "download now" (TriggerManualDownloadUseCase) shares
// the same DownloadConcurrencyTracker and ExecutorService rather than
// running a second dispatch path.
@Component
public class RunDownloadQueueTickUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunDownloadQueueTickUseCase.class);

    private final AppSettingsRepository appSettingsRepository;
    private final DownloadItemRepository downloadItemRepository;
    private final ProviderSettingsRepository providerSettingsRepository;
    private final DownloadConcurrencyTracker concurrencyTracker;
    private final ExecuteDownloadUseCase executeDownloadUseCase;
    private final ExecutorService downloadExecutor;

    public RunDownloadQueueTickUseCase(AppSettingsRepository appSettingsRepository,
                                        DownloadItemRepository downloadItemRepository,
                                        ProviderSettingsRepository providerSettingsRepository,
                                        DownloadConcurrencyTracker concurrencyTracker,
                                        ExecuteDownloadUseCase executeDownloadUseCase,
                                        ExecutorService downloadExecutor) {
        this.appSettingsRepository = appSettingsRepository;
        this.downloadItemRepository = downloadItemRepository;
        this.providerSettingsRepository = providerSettingsRepository;
        this.concurrencyTracker = concurrencyTracker;
        this.executeDownloadUseCase = executeDownloadUseCase;
        this.downloadExecutor = downloadExecutor;
    }

    public void tick() {
        AppSettings settings = appSettingsRepository.findById(1L).orElseThrow();
        if (!settings.allowsDownloadsAt(LocalTime.now())) {
            return;
        }

        Map<String, DownloadPolicy> policyByCreator = providerSettingsRepository.findAll().stream()
                .collect(Collectors.toMap(ProviderSettings::getProviderId, ProviderSettings::getDownloadPolicy));

        List<DownloadItem> candidates = downloadItemRepository.findEligibleForAutomaticDownload(LocalDateTime.now());
        int dispatched = 0;
        for (DownloadItem candidate : candidates) {
            if (policyByCreator.get(candidate.getSource().getCreator()) != DownloadPolicy.EAGER) {
                continue;
            }
            if (!concurrencyTracker.tryAcquire(settings.getMaxConcurrentDownloads())) {
                break;
            }
            dispatchAsync(candidate.getId());
            dispatched++;
        }
        if (dispatched > 0) {
            log.info("Queue tick dispatched {} download(s)", dispatched);
        }
    }

    void dispatchAsync(UUID itemId) {
        downloadExecutor.submit(() -> {
            try {
                executeDownloadUseCase.execute(itemId);
            } catch (Exception e) {
                // Belt-and-braces - ExecuteDownloadUseCase already catches
                // everything internally, but a submitted task throwing
                // would otherwise vanish silently into the executor.
                log.error("Unhandled exception dispatching item {}", itemId, e);
            } finally {
                concurrencyTracker.release();
            }
        });
    }
}
