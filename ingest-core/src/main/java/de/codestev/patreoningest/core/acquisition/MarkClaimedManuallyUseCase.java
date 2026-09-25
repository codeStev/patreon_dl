package de.codestev.patreoningest.core.acquisition;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

// The operator claimed a source by hand in their own browser (typically one
// ClaimQueueJob handed off as NEEDS_MANUAL) and confirms it in the admin UI.
@Component
public class MarkClaimedManuallyUseCase {

    public enum Result {
        MARKED_CLAIMED, NOT_FOUND, ALREADY_CLAIMED
    }

    private final DownloadSourceRepository downloadSourceRepository;

    public MarkClaimedManuallyUseCase(DownloadSourceRepository downloadSourceRepository) {
        this.downloadSourceRepository = downloadSourceRepository;
    }

    @Transactional
    public Result markClaimed(UUID sourceId) {
        Optional<DownloadSource> maybeSource = downloadSourceRepository.findById(sourceId);
        if (maybeSource.isEmpty()) {
            return Result.NOT_FOUND;
        }
        DownloadSource source = maybeSource.get();
        if (source.getClaimStatus() == ClaimStatus.CLAIMED) {
            return Result.ALREADY_CLAIMED;
        }
        source.markClaimed();
        downloadSourceRepository.save(source);
        return Result.MARKED_CLAIMED;
    }
}
