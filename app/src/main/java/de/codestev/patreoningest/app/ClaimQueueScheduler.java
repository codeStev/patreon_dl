package de.codestev.patreoningest.app;

import de.codestev.patreoningest.core.acquisition.ClaimQueueJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// Same fixed-cadence, disabled-in-tests shape as FolderSyncScheduler. 10
// minutes default - new claims only arrive with new emails, and each
// attempt drives a real browser, so there's no reason to be eager.
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "patreon.acquisition.claim-queue", name = "enabled", matchIfMissing = true)
public class ClaimQueueScheduler {

    private final ClaimQueueJob claimQueueJob;

    public ClaimQueueScheduler(ClaimQueueJob claimQueueJob) {
        this.claimQueueJob = claimQueueJob;
    }

    @Scheduled(fixedDelayString = "${patreon.acquisition.claim-queue-interval-ms:600000}")
    public void run() {
        claimQueueJob.runDue();
    }
}
