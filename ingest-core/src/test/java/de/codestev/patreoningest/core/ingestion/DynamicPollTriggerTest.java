package de.codestev.patreoningest.core.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicPollTriggerTest {

    private final IngestionSettingsRepository repository = mock(IngestionSettingsRepository.class);
    private final DynamicPollTrigger trigger = new DynamicPollTrigger(repository);

    @Test
    void pollsImmediatelyOnTheFirstRunRegardlessOfConfiguredInterval() {
        when(repository.findById(1L)).thenReturn(Optional.of(new IngestionSettings(1L, 300)));

        Instant next = trigger.nextExecution(new FakeTriggerContext(null));

        assertThat(next).isCloseTo(Instant.now(), within(2, ChronoUnit.SECONDS));
    }

    @Test
    void schedulesTheNextRunAtLastExecutionPlusTheConfiguredInterval() {
        when(repository.findById(1L)).thenReturn(Optional.of(new IngestionSettings(1L, 120)));
        Instant lastRun = Instant.now().minusSeconds(60);

        Instant next = trigger.nextExecution(new FakeTriggerContext(lastRun));

        assertThat(next).isEqualTo(lastRun.plusSeconds(120));
    }

    @Test
    void aChangedIntervalTakesEffectOnTheVeryNextComputation() {
        Instant lastRun = Instant.now();
        when(repository.findById(1L)).thenReturn(Optional.of(new IngestionSettings(1L, 300)));
        assertThat(trigger.nextExecution(new FakeTriggerContext(lastRun))).isEqualTo(lastRun.plusSeconds(300));

        // Simulates the operator changing the setting via the UI between ticks.
        when(repository.findById(1L)).thenReturn(Optional.of(new IngestionSettings(1L, 30)));
        assertThat(trigger.nextExecution(new FakeTriggerContext(lastRun))).isEqualTo(lastRun.plusSeconds(30));
    }

    @Test
    void fallsBackToTheDefaultIntervalWhenNoSettingsRowExists() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        Instant lastRun = Instant.now();

        Instant next = trigger.nextExecution(new FakeTriggerContext(lastRun));

        assertThat(next).isEqualTo(lastRun.plusSeconds(DynamicPollTrigger.DEFAULT_INTERVAL_SECONDS));
    }

    private record FakeTriggerContext(Instant lastActualExecution) implements TriggerContext {
        @Override
        public Clock getClock() {
            return Clock.systemDefaultZone();
        }

        @Override
        public Instant lastScheduledExecution() {
            return lastActualExecution;
        }

        @Override
        public Instant lastActualExecution() {
            return lastActualExecution;
        }

        @Override
        public Instant lastCompletion() {
            return lastActualExecution;
        }
    }
}
