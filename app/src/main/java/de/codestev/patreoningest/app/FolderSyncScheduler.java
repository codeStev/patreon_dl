package de.codestev.patreoningest.app;

import de.codestev.patreoningest.core.acquisition.FolderSyncJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// Same fixed-cadence, disabled-in-tests shape as DownloadQueueScheduler /
// MailboxPollingScheduler. 30 minutes default - lsjson is a real Drive API
// call per claimed source, so this deliberately isn't as frequent as the
// download queue tick.
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "patreon.fulfillment.folder-sync", name = "enabled", matchIfMissing = true)
public class FolderSyncScheduler {

    private final FolderSyncJob folderSyncJob;

    public FolderSyncScheduler(FolderSyncJob folderSyncJob) {
        this.folderSyncJob = folderSyncJob;
    }

    @Scheduled(fixedDelayString = "${patreon.fulfillment.folder-sync-interval-ms:1800000}")
    public void sync() {
        folderSyncJob.syncAll();
    }
}
