package de.codestev.patreoningest.core.fulfillment;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

// Not a java.util.concurrent.Semaphore: a semaphore's permit count is fixed
// at construction (or requires reducePermits(), which isn't safe to call
// concurrently with acquisitions). max_concurrent_downloads must be
// live-editable from the UI, so the limit is re-read fresh from AppSettings
// on every acquire attempt instead of being baked in anywhere.
@Component
public class DownloadConcurrencyTracker {

    private final AtomicInteger inFlight = new AtomicInteger(0);

    public boolean tryAcquire(int maxAllowed) {
        while (true) {
            int current = inFlight.get();
            if (current >= maxAllowed) {
                return false;
            }
            if (inFlight.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void release() {
        inFlight.updateAndGet(current -> Math.max(0, current - 1));
    }

    public int current() {
        return inFlight.get();
    }
}
