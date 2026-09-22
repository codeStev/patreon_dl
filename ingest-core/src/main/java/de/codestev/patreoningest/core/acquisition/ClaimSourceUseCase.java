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

        // A registered ClaimPort performs the real external action (e.g.
        // Gumroad checkout); no port at all means claiming is implicit for
        // this source type (DRIVE, MMF) - either way the source ends up
        // CLAIMED.
        port.ifPresent(p -> p.claim(source));

        source.markClaimed();
        downloadSourceRepository.save(source);
    }
}
