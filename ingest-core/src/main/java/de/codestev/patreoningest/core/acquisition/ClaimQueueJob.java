package de.codestev.patreoningest.core.acquisition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Drives port-backed claims (e.g. Gumroad checkout) in the background, one
// source at a time - both because a claim is slow (a real browser) and to
// stay gentle with the claim target. A transient failure is retried with
// exponential backoff; after MAX_ATTEMPTS it's handed to the operator
// (NEEDS_MANUAL) instead of retrying forever.
@Component
public class ClaimQueueJob {

    private static final Logger log = LoggerFactory.getLogger(ClaimQueueJob.class);

    static final int MAX_ATTEMPTS = 5;
    private static final Duration BASE_BACKOFF = Duration.ofMinutes(30);

    private final List<ClaimPort> claimPorts;
    private final DownloadSourceRepository downloadSourceRepository;

    public ClaimQueueJob(List<ClaimPort> claimPorts, DownloadSourceRepository downloadSourceRepository) {
        this.claimPorts = claimPorts;
        this.downloadSourceRepository = downloadSourceRepository;
    }

    public void runDue() {
        List<DownloadSource> due = downloadSourceRepository.findClaimsDue(LocalDateTime.now());
        for (DownloadSource source : due) {
            Optional<ClaimPort> port = claimPorts.stream()
                    .filter(p -> p.supports() == source.getSourceType())
                    .findFirst();
            if (port.isEmpty()) {
                // Adapter not available (e.g. not configured) - leave the
                // source DISCOVERED, untouched, until one is.
                continue;
            }
            claimOne(source, port.get());
        }
    }

    private void claimOne(DownloadSource source, ClaimPort port) {
        ClaimOutcome outcome;
        try {
            outcome = port.claim(source);
        } catch (RuntimeException e) {
            // One misbehaving claim must never stop the rest of the queue.
            log.error("Claim attempt threw for source {} ({})", source.getId(), source.getSourceUrl(), e);
            outcome = new ClaimOutcome.RetryLater(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        switch (outcome) {
            case ClaimOutcome.Claimed claimed -> {
                source.markClaimed(claimed.receiptUrl());
                log.info("Claimed source {} ({})", source.getId(), source.getSourceUrl());
            }
            case ClaimOutcome.AlreadyOwned ignored -> {
                source.markClaimedAlreadyOwned();
                log.info("Source {} was already owned - marked claimed ({})", source.getId(), source.getSourceUrl());
            }
            case ClaimOutcome.NeedsManual manual -> {
                source.markNeedsManualClaim(manual.reason());
                log.warn("Source {} needs a manual claim: {}", source.getId(), manual.reason());
            }
            case ClaimOutcome.RetryLater retry -> recordFailedAttempt(source, retry.reason());
        }
        downloadSourceRepository.save(source);
    }

    private void recordFailedAttempt(DownloadSource source, String reason) {
        if (source.getClaimAttempts() + 1 >= MAX_ATTEMPTS) {
            source.markNeedsManualClaim("Gave up after " + MAX_ATTEMPTS + " attempts - last error: " + reason);
            log.warn("Source {} needs a manual claim after {} failed attempts: {}",
                    source.getId(), MAX_ATTEMPTS, reason);
            return;
        }
        Duration backoff = BASE_BACKOFF.multipliedBy(1L << source.getClaimAttempts());
        source.markClaimAttemptFailed(reason, LocalDateTime.now().plus(backoff));
        log.warn("Claim attempt {} for source {} failed, retrying in {}: {}",
                source.getClaimAttempts(), source.getId(), backoff, reason);
    }
}
