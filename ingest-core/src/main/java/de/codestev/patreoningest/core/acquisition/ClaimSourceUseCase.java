package de.codestev.patreoningest.core.acquisition;

import org.springframework.stereotype.Component;

// Runs at registration time, inline with mailbox polling - so it only ever
// performs the implicit claim (DRIVE/MMF: nothing external to do). Sources
// that need a real external action (claimType != NONE, e.g. Gumroad) are
// left DISCOVERED here and picked up by ClaimQueueJob in the background.
@Component
public class ClaimSourceUseCase {

    private final DownloadSourceRepository downloadSourceRepository;

    public ClaimSourceUseCase(DownloadSourceRepository downloadSourceRepository) {
        this.downloadSourceRepository = downloadSourceRepository;
    }

    public void claim(DownloadSource source) {
        if (source.getClaimStatus() != ClaimStatus.DISCOVERED) {
            return;
        }
        if (source.getClaimType() != null && source.getClaimType() != ClaimType.NONE) {
            return;
        }

        source.markClaimed();
        downloadSourceRepository.save(source);
    }
}
