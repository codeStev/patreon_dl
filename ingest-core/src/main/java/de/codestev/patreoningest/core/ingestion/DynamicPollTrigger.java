package de.codestev.patreoningest.core.ingestion;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;

// Re-reads the configured interval from the DB on every scheduling
// decision (right after each run, before computing the next one) - a
// change made via the UI takes effect starting from the next run, without
// needing to cancel/reschedule anything. No caching: this is read at most
// once per poll cycle (minutes apart), nowhere near the hot-loop frequency
// that would justify it (contrast with the download queue's per-tick
// settings reads).
public class DynamicPollTrigger implements Trigger {

    public static final int DEFAULT_INTERVAL_SECONDS = 300;

    private final IngestionSettingsRepository ingestionSettingsRepository;

    public DynamicPollTrigger(IngestionSettingsRepository ingestionSettingsRepository) {
        this.ingestionSettingsRepository = ingestionSettingsRepository;
    }

    @Override
    public Instant nextExecution(TriggerContext triggerContext) {
        Instant lastActualExecution = triggerContext.lastActualExecution();
        if (lastActualExecution == null) {
            // No prior run - poll once immediately on startup rather than
            // making the operator wait a full interval for the first check.
            return Instant.now();
        }
        return lastActualExecution.plusSeconds(currentIntervalSeconds());
    }

    private int currentIntervalSeconds() {
        return ingestionSettingsRepository.findById(1L)
                .map(IngestionSettings::getPollIntervalSeconds)
                .orElse(DEFAULT_INTERVAL_SECONDS);
    }
}
