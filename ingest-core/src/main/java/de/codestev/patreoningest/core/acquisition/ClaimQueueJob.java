package de.codestev.patreoningest.core.acquisition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final ProviderSettingsRepository providerSettingsRepository;
    // The set of active ports is fixed for the life of the process, so a
    // missing adapter is warned about once per source type, not every run.
    private final Set<SourceType> warnedMissingAdapter = EnumSet.noneOf(SourceType.class);

    public ClaimQueueJob(List<ClaimPort> claimPorts, DownloadSourceRepository downloadSourceRepository,
                         ProviderSettingsRepository providerSettingsRepository) {
        this.claimPorts = claimPorts;
        this.downloadSourceRepository = downloadSourceRepository;
        this.providerSettingsRepository = providerSettingsRepository;
    }

    public void runDue() {
        List<DownloadSource> due = downloadSourceRepository.findClaimsDue(LocalDateTime.now());
        Map<SourceType, Integer> skippedWithoutAdapter = new EnumMap<>(SourceType.class);
        for (DownloadSource source : due) {
            if (claimPolicyOf(source.getCreator()) == ClaimPolicy.MANUAL) {
                source.markNeedsManualClaim("Auto-redeem is off for " + source.getCreator() + " - claim it by hand");
                downloadSourceRepository.save(source);
                continue;
            }
            Optional<ClaimPort> port = claimPorts.stream()
                    .filter(p -> p.supports() == source.getSourceType())
                    .findFirst();
            if (port.isEmpty()) {
                // Adapter not available (e.g. not configured) - leave the
                // source DISCOVERED, untouched, until one is.
                skippedWithoutAdapter.merge(source.getSourceType(), 1, Integer::sum);
                continue;
            }
            claimOne(source, port.get());
        }
        warnAboutMissingAdapters(skippedWithoutAdapter);
    }

    private void warnAboutMissingAdapters(Map<SourceType, Integer> skipped) {
        skipped.forEach((type, count) -> {
            if (warnedMissingAdapter.add(type)) {
                log.warn("No claim adapter is active for {} - {} due claim(s) stay DISCOVERED until one is "
                        + "configured (for GUMROAD: set patreon.acquisition.gumroad.email / GUMROAD_EMAIL)",
                        type, count);
            }
        });
    }

    private ClaimPolicy claimPolicyOf(String providerId) {
        return providerSettingsRepository.findById(providerId)
                .map(ProviderSettings::getClaimPolicy)
                .orElse(ClaimPolicy.AUTO);
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
