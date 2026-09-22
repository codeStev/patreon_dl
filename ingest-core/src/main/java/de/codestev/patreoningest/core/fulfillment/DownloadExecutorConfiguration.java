package de.codestev.patreoningest.core.fulfillment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Virtual-thread-per-task, not a fixed-size pool: the actual concurrency
// limit is enforced by DownloadConcurrencyTracker against the live
// max_concurrent_downloads setting, not by thread-pool sizing (see the
// design doc's explicit requirement that this not be baked in at startup).
@Configuration
class DownloadExecutorConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService downloadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
