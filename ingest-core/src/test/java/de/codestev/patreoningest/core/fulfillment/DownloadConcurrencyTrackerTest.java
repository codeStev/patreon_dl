package de.codestev.patreoningest.core.fulfillment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadConcurrencyTrackerTest {

    private final DownloadConcurrencyTracker tracker = new DownloadConcurrencyTracker();

    @Test
    void acquiresUpToTheConfiguredMaximum() {
        assertThat(tracker.tryAcquire(2)).isTrue();
        assertThat(tracker.tryAcquire(2)).isTrue();
        assertThat(tracker.tryAcquire(2)).isFalse();
        assertThat(tracker.current()).isEqualTo(2);
    }

    @Test
    void releasingFreesUpASlot() {
        tracker.tryAcquire(1);
        assertThat(tracker.tryAcquire(1)).isFalse();

        tracker.release();

        assertThat(tracker.tryAcquire(1)).isTrue();
    }

    @Test
    void aLiveLoweredMaximumIsRespectedOnTheNextAcquireAttempt() {
        tracker.tryAcquire(5);
        tracker.tryAcquire(5);

        // Operator lowers max_concurrent_downloads via the UI mid-flight -
        // no baked-in pool size to fight against.
        assertThat(tracker.tryAcquire(2)).isFalse();
    }

    @Test
    void releaseNeverGoesNegative() {
        tracker.release();
        tracker.release();

        assertThat(tracker.current()).isEqualTo(0);
    }
}
