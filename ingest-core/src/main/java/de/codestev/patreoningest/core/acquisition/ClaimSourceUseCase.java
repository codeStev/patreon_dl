package de.codestev.patreoningest.core.acquisition;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ClaimSourceUseCase {

    private final List<ClaimPort> claimPorts;
    private final DownloadSourceRepository downloadSourceRepository;

    public ClaimSourceUseCase(List<ClaimPort> claimPorts, DownloadSourceRepository downloadSourceRepository) {
        this.claimPorts = claimPorts;
        this.downloadSourceRepository = downloadSourceRepository;
    }

    public void claim(DownloadSource source) {
        if (source.getClaimStatus() == ClaimStatus.CLAIMED) {
            return;
        }

        Optional<ClaimPort> port = claimPorts.stream()
                .filter(p -> p.supports() == source.getSourceType())
                .findFirst();

        if (port.isEmpty() && source.getClaimType() != ClaimType.NONE) {
            // A claim mechanism is genuinely expected for this source type
            // (e.g. Gumroad) but no adapter for it exists yet - leave it
            // DISCOVERED rather than silently (and incorrectly) marking it
            // claimed. This is different from DRIVE/MMF, which have no
            // ClaimPort because none is ever needed, not because one is
            // missing.
            return;
        }

        // A registered ClaimPort performs the real external action (e.g.
        // Gumroad checkout); no port at all AND claimType == NONE means
        // claiming is implicit for this source type (DRIVE, MMF).
        port.ifPresent(p -> p.claim(source));

        source.markClaimed();
        downloadSourceRepository.save(source);
    }
}
