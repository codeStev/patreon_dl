package de.codestev.patreoningest.core.acquisition;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

// Undoes AddManualSourceUseCase: the operator no longer wants a link they
// added by hand. Email-parsed sources can't be removed this way - the next
// mailbox scan would only re-register them.
//
// Removes the source and its items from the database only. Files already
// downloaded stay on disk untouched - the operator moves them out of the
// download root anyway, and deleting data from a UI click isn't worth the
// risk.
@Component
public class RemoveManualSourceUseCase {

    public enum Result {
        REMOVED, NOT_FOUND, NOT_ADDED_MANUALLY
    }

    private final DownloadSourceRepository downloadSourceRepository;
    private final DownloadItemRepository downloadItemRepository;
    private final ProviderSettingsRepository providerSettingsRepository;
    private final ManualSources manualSources;

    public RemoveManualSourceUseCase(DownloadSourceRepository downloadSourceRepository,
                                     DownloadItemRepository downloadItemRepository,
                                     ProviderSettingsRepository providerSettingsRepository,
                                     ManualSources manualSources) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.downloadItemRepository = downloadItemRepository;
        this.providerSettingsRepository = providerSettingsRepository;
        this.manualSources = manualSources;
    }

    @Transactional
    public Result remove(UUID sourceId) {
        Optional<DownloadSource> maybeSource = downloadSourceRepository.findById(sourceId);
        if (maybeSource.isEmpty()) {
            return Result.NOT_FOUND;
        }
        DownloadSource source = maybeSource.get();
        if (!manualSources.isAddedManually(source)) {
            return Result.NOT_ADDED_MANUALLY;
        }

        downloadItemRepository.deleteBySourceId(sourceId);
        downloadSourceRepository.delete(source);
        downloadSourceRepository.flush();
        // The name's Settings entry only exists for its links - drop it
        // with the last one so Settings doesn't collect dead entries.
        if (!downloadSourceRepository.existsByCreator(source.getCreator())) {
            providerSettingsRepository.deleteById(source.getCreator());
        }
        return Result.REMOVED;
    }
}
