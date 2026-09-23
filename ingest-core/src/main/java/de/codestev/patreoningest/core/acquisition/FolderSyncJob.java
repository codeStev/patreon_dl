package de.codestev.patreoningest.core.acquisition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

// The folder is the source of truth for what files exist, not the email
// text (see the design doc's "Handling folders that fill in over time").
// Runs sources sequentially, one at a time - not just the download queue's
// own concurrency cap, but a separate restraint against hammering Drive's
// API with this job's own listing calls.
@Component
public class FolderSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FolderSyncJob.class);

    // Real observed data: a Nomnom link sometimes points directly at ONE
    // model's own folder (e.g. "Chibi He-Man") whose top-level contents are
    // organizational subfolders like "STL"/"Render Images"/"Presupport",
    // not separate models - as opposed to the classic case of a folder
    // containing several actual model subfolders side by side. When every
    // top-level entry's name matches this known vocabulary, the whole
    // folder is treated as one model instead of enumerating each entry as
    // if it were its own. Deliberately requires ALL entries to match (not
    // just some) - a real multi-model folder's entries are character names
    // that won't coincidentally collide with this list.
    private static final Set<String> ORGANIZATIONAL_SUBFOLDER_NAMES = Set.of(
            "stl", "stls", "render images", "renders", "render", "presupport",
            "pre supported", "presupported", "supported", "unsupported", "uncut",
            "textures", "images", "preview", "previews", "parts", "chitubox", "lys"
    );

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
        Optional<String> folderId = GoogleDriveUrls.tryExtractFolderId(source.getSourceUrl());
        if (folderId.isPresent()) {
            syncFolder(source, folderId.get());
            return;
        }

        Optional<String> fileId = GoogleDriveUrls.tryExtractFileId(source.getSourceUrl());
        if (fileId.isPresent()) {
            syncSingleFile(source, fileId.get());
            return;
        }

        // Neither shape recognized - deterministically unfixable by
        // retrying, unlike a real rclone/network failure. Flag it once so
        // it's visible (reusing link_dead - there's no separate
        // "unsupported" status) and excluded from every future sync,
        // rather than logging the same error every 30 minutes forever.
        log.warn("Source {} ({}) is not a recognizable Drive folder or file link - "
                        + "flagging link-dead instead of retrying forever",
                source.getId(), source.getSourceUrl());
        source.markLinkDead();
        downloadSourceRepository.save(source);
    }

    private void syncFolder(DownloadSource source, String folderId) {
        List<DriveEntry> entries = driveFolderListing.list(folderId);

        if (looksLikeASingleModelsOwnFolder(entries)) {
            syncWholeFolderAsOneModel(source, folderId, entries.size());
            return;
        }

        boolean foundNew = false;
        for (DriveEntry entry : entries) {
            if (downloadItemRepository.findBySourceIdAndRemoteFileId(source.getId(), entry.id()).isPresent()) {
                continue;
            }
            String modelName = entry.isDirectory() ? entry.name() : stripExtension(entry.name());
            downloadItemRepository.save(new DownloadItem(source, modelName, entry.id(), entry.isDirectory()));
            foundNew = true;
        }

        source.markSynced(foundNew);
        downloadSourceRepository.save(source);
        log.info("Synced source {} - {} entr{} found, {}", source.getId(), entries.size(),
                entries.size() == 1 ? "y" : "ies", foundNew ? "new item(s) registered" : "nothing new");
    }

    private static boolean looksLikeASingleModelsOwnFolder(List<DriveEntry> entries) {
        return !entries.isEmpty() && entries.stream().allMatch(FolderSyncJob::isOrganizationalName);
    }

    private static boolean isOrganizationalName(DriveEntry entry) {
        String normalized = entry.name().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim();
        return ORGANIZATIONAL_SUBFOLDER_NAMES.contains(normalized);
    }

    // The folder IS one model (its top-level entries are organizational
    // subfolders, not separate models) - register the whole folder as a
    // single item instead of enumerating its contents as if they were
    // distinct models. A plain folder-copy of the whole thing already
    // recurses into whatever's inside correctly.
    private void syncWholeFolderAsOneModel(DownloadSource source, String folderId, int entryCount) {
        boolean isNew = downloadItemRepository.findBySourceIdAndRemoteFileId(source.getId(), folderId).isEmpty();
        if (isNew) {
            downloadItemRepository.save(new DownloadItem(source, fallbackModelLabel(source), folderId, true));
            log.info("Source {} looks like a single model's own folder ({} organizational subfolder(s)) - "
                    + "registering as one item instead of enumerating", source.getId(), entryCount);
        }
        source.markSynced(isNew);
        downloadSourceRepository.save(source);
    }

    // The whole source IS one file (e.g. a Nomnom-style whole-folder
    // registration that turned out to point at a single Drive file
    // instead of a folder, per real production data) - nothing to
    // list/diff, just ensure exactly one item exists for it.
    private void syncSingleFile(DownloadSource source, String fileId) {
        boolean isNew = downloadItemRepository.findBySourceIdAndRemoteFileId(source.getId(), fileId).isEmpty();
        if (isNew) {
            downloadItemRepository.save(new DownloadItem(source, fallbackModelLabel(source), fileId, false));
            log.info("Synced single-file source {} - registered", source.getId());
        }
        source.markSynced(isNew);
        downloadSourceRepository.save(source);
    }

    // We don't know the folder/file's real name without an extra Drive API
    // call at sync time - the actual downloaded content on disk will still
    // be organized correctly (rclone resolves real names for a folder copy;
    // a single-file copy uses the file's own real name); this is only the
    // label shown in the admin UI until then.
    private static String fallbackModelLabel(DownloadSource source) {
        if (source.getMonthLabel() != null) {
            return source.getMonthLabel();
        }
        if (source.getCategory() != null) {
            return source.getCategory();
        }
        return "Shared folder";
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
