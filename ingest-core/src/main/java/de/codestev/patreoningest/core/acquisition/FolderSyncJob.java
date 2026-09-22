package de.codestev.patreoningest.core.acquisition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

// The folder is the source of truth for what files exist, not the email
// text (see the design doc's "Handling folders that fill in over time").
// Runs sources sequentially, one at a time - not just the download queue's
// own concurrency cap, but a separate restraint against hammering Drive's
// API with this job's own listing calls.
@Component
public class FolderSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FolderSyncJob.class);

    private final DownloadSourceRepository downloadSourceRepository;
    private final DownloadItemRepository downloadItemRepository;
    private final DriveFolderListing driveFolderListing;

    public FolderSyncJob(DownloadSourceRepository downloadSourceRepository,
                          DownloadItemRepository downloadItemRepository,
                          DriveFolderListing driveFolderListing) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.downloadItemRepository = downloadItemRepository;
        this.driveFolderListing = driveFolderListing;
    }

    public void syncAll() {
        List<DownloadSource> candidates = downloadSourceRepository
                .findBySourceTypeAndClaimStatusAndLinkDead(SourceType.DRIVE, ClaimStatus.CLAIMED, false);
        log.info("Folder sync starting - {} claimed Drive source(s) to check", candidates.size());
        for (DownloadSource source : candidates) {
            try {
                syncOne(source);
            } catch (Exception e) {
                // One dead/misbehaving folder must never stop the rest of
                // the sync - same resilience rule as PollMailboxUseCase.
                log.error("Folder sync failed for source {} ({})", source.getId(), source.getSourceUrl(), e);
            }
        }
        log.info("Folder sync complete");
    }

    private void syncOne(DownloadSource source) {
        String folderId;
        try {
            folderId = GoogleDriveUrls.extractFolderId(source.getSourceUrl());
        } catch (IllegalArgumentException e) {
            // Not a folder link at all (e.g. a Drive single-file share,
            // "/file/d/<id>/view", which a parser can legitimately produce
            // but this job only ever handles folders) - deterministically
            // unfixable by retrying, unlike a real rclone/network failure.
            // Flag it once so it's visible (reusing link_dead - there's no
            // separate "unsupported" status) and excluded from every future
            // sync, rather than logging the same error every 30 minutes
            // forever.
            log.warn("Source {} ({}) is not a Drive folder link - flagging link-dead instead of retrying forever",
                    source.getId(), source.getSourceUrl());
            source.markLinkDead();
            downloadSourceRepository.save(source);
            return;
        }

        List<DriveEntry> entries = driveFolderListing.list(folderId);

        boolean foundNew = false;
        for (DriveEntry entry : entries) {
            if (downloadItemRepository.findBySourceIdAndRemoteFileId(source.getId(), entry.id()).isPresent()) {
                continue;
            }
            String modelName = entry.isDirectory() ? entry.name() : stripExtension(entry.name());
            downloadItemRepository.save(new DownloadItem(source, modelName, entry.id()));
            foundNew = true;
        }

        source.markSynced(foundNew);
        downloadSourceRepository.save(source);
        log.info("Synced source {} - {} entr{} found, {}", source.getId(), entries.size(),
                entries.size() == 1 ? "y" : "ies", foundNew ? "new item(s) registered" : "nothing new");
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
