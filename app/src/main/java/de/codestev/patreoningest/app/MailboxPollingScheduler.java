package de.codestev.patreoningest.app;

import de.codestev.patreoningest.core.ingestion.DynamicPollTrigger;
import de.codestev.patreoningest.core.ingestion.IngestionSettingsRepository;
import de.codestev.patreoningest.core.ingestion.PollMailboxUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// Application-assembly concern (like @EntityScan/@EnableJpaRepositories on
// Application), not a library concern - lives here, not in ingest-core, so
// it never activates during ingest-core's own persistence/domain tests via
// TestApplication. Disabled in app's own tests too
// (patreon.ingestion.polling.enabled=false in test properties) so it can't
// race against a test's own explicit poll() calls.
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "patreon.ingestion.polling", name = "enabled", matchIfMissing = true)
public class MailboxPollingScheduler implements SchedulingConfigurer {

    private final PollMailboxUseCase pollMailboxUseCase;
    private final IngestionSettingsRepository ingestionSettingsRepository;

    public MailboxPollingScheduler(PollMailboxUseCase pollMailboxUseCase,
                                    IngestionSettingsRepository ingestionSettingsRepository) {
        this.pollMailboxUseCase = pollMailboxUseCase;
        this.ingestionSettingsRepository = ingestionSettingsRepository;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                pollMailboxUseCase::poll,
                new DynamicPollTrigger(ingestionSettingsRepository));
    }
}
