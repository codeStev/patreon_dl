package de.codestev.patreoningest.app;

import de.codestev.patreoningest.core.fulfillment.RunDownloadQueueTickUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// Fixed application-level cadence, unlike the ingestion poll interval -
// not DB-dynamic/UI-configurable in this pass (see the Fulfillment plan's
// "Explicitly out of scope"). Disabled in app's own tests
// (patreon.fulfillment.queue.enabled=false) so it can't race against a
// test's own explicit tick()/execute() calls, same shape as
// MailboxPollingScheduler.
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "patreon.fulfillment.queue", name = "enabled", matchIfMissing = true)
public class DownloadQueueScheduler {

    private final RunDownloadQueueTickUseCase runDownloadQueueTickUseCase;

    public DownloadQueueScheduler(RunDownloadQueueTickUseCase runDownloadQueueTickUseCase) {
        this.runDownloadQueueTickUseCase = runDownloadQueueTickUseCase;
    }

    @Scheduled(fixedDelayString = "${patreon.fulfillment.queue-tick-interval-ms:15000}")
    public void tick() {
        runDownloadQueueTickUseCase.tick();
    }
}
