package de.codestev.patreoningest.core.acquisition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    // organizational subfolders like "STL"/"Render Images"/"Presupports",
    // not separate models - as opposed to the classic case of a folder
    // containing several actual model subfolders side by side. See
    // SingleModelFolder for how that is decided (all entries must be
    // organizational; a stray cover or readme doesn't count against it).

    private final DownloadSourceRepository downloadSourceRepository;
    private final DownloadItemRepository downloadItemRepository;
    private final DriveFolderListing driveFolderListing;
    private final DriveFolderTitles driveFolderTitles;

    public FolderSyncJob(DownloadSourceRepository downloadSourceRepository,
                          DownloadItemRepository downloadItemRepository,
                          DriveFolderListing driveFolderListing,
                          DriveFolderTitles driveFolderTitles) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.downloadItemRepository = downloadItemRepository;
        this.driveFolderListing = driveFolderListing;
        this.driveFolderTitles = driveFolderTitles;
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
        // A directly-named item (null remoteFileId) means the parser
        // already fully represents this source as a clean 1:1
        // model<->link mapping (Bulkamancer standalone DMs, Wicked
        // per-model Gumroad lines) - per the design doc, "there's nothing
        // to diff" here. Real incident this fixes: a Bulkamancer standalone
        // link's folder also contains variant subfolders (e.g.
        // "wolverine_no_supports", "wolverine_uncut") alongside a Readme -
        // without this check, every one of those got enumerated as its own
        // spurious top-level "model" even though the named item already
        // downloads the whole folder (recursively, variants included) as
        // one unit.
        if (downloadItemRepository.existsBySourceIdAndRemoteFileIdIsNull(source.getId())) {
            return;
        }

        Optional<String> folderId = GoogleDriveUrls.tryExtractFolderId(source.getSourceUrl());
        if (folderId.isPresent()) {
            if (source.getFolderLayout() == FolderLayout.COLLECTIONS) {
                syncCollections(source, folderId.get());
            } else {
                syncFolder(source, folderId.get());
            }
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
            syncWholeFolderAsOneModel(source, folderId, entries);
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

    // A persistent link whose top-level folders group collections - usually
    // one folder per creator holding one folder per monthly release, but
    // real links mix in other top-level folders ("Terrain Pack", a
    // year-range archive), so this never assumes every top-level folder is
    // a creator:
    //  - a top-level folder's children each become one item, grouped under
    //    that folder's name;
    //  - a top-level folder that is one model's own folder (only
    //    organizational children) becomes one item itself;
    //  - a top-level file becomes one item.
    // An empty top-level folder registers nothing yet and is simply looked
    // at again next sync. Identity is the Drive ID, as everywhere else, so
    // a collection that rotates out of the link keeps its item.
    private void syncCollections(DownloadSource source, String folderId) {
        List<NestedDriveEntry> entries = driveFolderListing.listTwoLevels(folderId);
        // Keyed by the parent's name, since that's all a nested entry
        // carries. Two top-level folders sharing a name would merge here -
        // rclone can't tell them apart by path either.
        Map<String, List<DriveEntry>> childrenByParent = entries.stream()
                .filter(entry -> !entry.isTopLevel())
                .collect(Collectors.groupingBy(NestedDriveEntry::parentName, LinkedHashMap::new,
                        Collectors.mapping(NestedDriveEntry::entry, Collectors.toList())));

        boolean foundNew = false;
        int topLevelCount = 0;
        for (NestedDriveEntry nested : entries) {
            if (!nested.isTopLevel()) {
                continue;
            }
            topLevelCount++;
            DriveEntry topLevel = nested.entry();
            if (!topLevel.isDirectory()) {
                foundNew |= registerIfNew(source, stripExtension(topLevel.name()), topLevel.id(), false, null);
                continue;
            }
            List<DriveEntry> children = childrenByParent.getOrDefault(topLevel.name(), List.of());
            if (looksLikeASingleModelsOwnFolder(children)) {
                foundNew |= registerIfNew(source, topLevel.name(), topLevel.id(), true, null);
                continue;
            }
            for (DriveEntry child : children) {
                String name = child.isDirectory() ? child.name() : stripExtension(child.name());
                foundNew |= registerIfNew(source, name, child.id(), child.isDirectory(), topLevel.name());
            }
        }

        source.markSynced(foundNew);
        downloadSourceRepository.save(source);
        log.info("Synced collections source {} - {} top-level entr{} found, {}", source.getId(), topLevelCount,
                topLevelCount == 1 ? "y" : "ies", foundNew ? "new item(s) registered" : "nothing new");
    }

    private boolean registerIfNew(DownloadSource source, String modelName, String remoteFileId,
                                  boolean isDirectory, String groupName) {
        if (downloadItemRepository.findBySourceIdAndRemoteFileId(source.getId(), remoteFileId).isPresent()) {
            return false;
        }
        downloadItemRepository.save(new DownloadItem(source, modelName, remoteFileId, isDirectory, groupName));
        return true;
    }

    private static boolean looksLikeASingleModelsOwnFolder(List<DriveEntry> entries) {
        return SingleModelFolder.matches(entries);
    }

    // The folder IS one model (its top-level entries are organizational
    // subfolders, not separate models) - register the whole folder as a
    // single item instead of enumerating its contents as if they were
    // distinct models. A plain folder-copy of the whole thing already
    // recurses into whatever's inside correctly.
    private void syncWholeFolderAsOneModel(DownloadSource source, String folderId, List<DriveEntry> entries) {
        boolean isNew = downloadItemRepository.findBySourceIdAndRemoteFileId(source.getId(), folderId).isEmpty();
        if (isNew) {
            downloadItemRepository.save(new DownloadItem(source, folderLabel(source, folderId), folderId, true));
            log.info("Source {} looks like a single model's own folder ({} entr{}) - "
                    + "registering as one item instead of enumerating", source.getId(), entries.size(),
                    entries.size() == 1 ? "y" : "ies");
            discardSplitLeftovers(source, folderId, entries);
        }
        source.markSynced(isNew);
        downloadSourceRepository.save(source);
    }

    // A link that was synced before it was recognized as one model's folder
    // has its subfolders registered as separate "models" ("STL", "Render
    // Images", ...). Those still waiting to be downloaded are dropped now
    // (the whole-folder item covers them); those already downloaded can't
    // be undone from here - their files sit in folders named after the
    // subfolder and need cleaning up by hand, so they are reported.
    private void discardSplitLeftovers(DownloadSource source, String folderId, List<DriveEntry> entries) {
        Set<String> subfolderIds = entries.stream().map(DriveEntry::id).collect(Collectors.toSet());
        for (DownloadItem item : downloadItemRepository.findBySourceId(source.getId())) {
            if (folderId.equals(item.getRemoteFileId()) || !subfolderIds.contains(item.getRemoteFileId())) {
                continue;
            }
            if (item.getStatus() == ItemStatus.PENDING) {
                downloadItemRepository.delete(item);
                log.info("Dropped the pending item '{}' of source {}: it is part of the single model registered now",
                        item.getModelName(), source.getId());
            } else {
                log.warn("Item '{}' of source {} was downloaded (status {}) as a separate model before the folder was "
                                + "recognized as one model - its files are in '{}' and must be sorted out by hand",
                        item.getModelName(), source.getId(), item.getStatus(), item.getLocalPath());
            }
        }
    }

    // The whole source IS one file (e.g. a Nomnom-style whole-folder
    // registration that turned out to point at a single Drive file
    // instead of a folder, per real production data) - nothing to
    // list/diff, just ensure exactly one item exists for it.
    private void syncSingleFile(DownloadSource source, String fileId) {
        boolean isNew = downloadItemRepository.findBySourceIdAndRemoteFileId(source.getId(), fileId).isEmpty();
        if (isNew) {
            downloadItemRepository.save(new DownloadItem(source, fallbackModelLabel(source, fileId), fileId, false));
            log.info("Synced single-file source {} - registered", source.getId());
        }
        source.markSynced(isNew);
        downloadSourceRepository.save(source);
    }

    // The folder's real name (read from its public share page), so the model
    // gets its own download directory and library name; the generic label if
    // that can't be read, or if another model of this creator already has
    // that name (two different folders must never merge into one directory).
    private String folderLabel(DownloadSource source, String folderId) {
        Optional<String> title = driveFolderTitles.titleOf(folderId);
        if (title.isEmpty()) {
            return fallbackModelLabel(source, folderId);
        }
        String name = title.get();
        if (downloadItemRepository.existsBySourceCreatorAndModelName(source.getCreator(), name)) {
            return name + " (" + folderId.substring(0, Math.min(8, folderId.length())) + ")";
        }
        return name;
    }

    // Getting the folder/file's real Drive-side name here isn't possible
    // with the rclone commands this app already shells out to (folderLabel
    // reads it from the share page instead): `lsjson
    // --stat` on a path rooted at this exact ID returns a synthetic empty
    // name for the root itself (confirmed by reading rclone's own
    // operations/lsjson.go source, not assumed), and the Drive API's
    // search query language (used by `rclone backend query`) doesn't
    // support filtering by "id" at all (confirmed against Google's own
    // query-terms reference) - only a raw files.get(id) call would work,
    // which needs its own Drive API HTTP client, not just shelling out to
    // rclone. So the label here is honestly generic, not the real
    // "Chibi He-Man"-style title - but qualified with a short ID suffix
    // so two different single-model folders/files NEVER collide into the
    // same download directory, which the merging bug this replaces would
    // have caused. The actual downloaded content on disk is still
    // organized correctly either way (rclone resolves real names for a
    // folder copy's contents; a single-file copy uses the file's own real
    // name) - only this admin-UI label and its top-level folder name are
    // generic.
    private static String fallbackModelLabel(DownloadSource source, String driveId) {
        String suffix = " (" + driveId.substring(0, Math.min(8, driveId.length())) + ")";
        if (source.getMonthLabel() != null) {
            return source.getMonthLabel() + suffix;
        }
        if (source.getCategory() != null) {
            return source.getCategory() + suffix;
        }
        return "Shared folder" + suffix;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
