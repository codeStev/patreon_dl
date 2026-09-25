package de.codestev.patreoningest.core.acquisition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DownloadItemRepository extends JpaRepository<DownloadItem, UUID> {

    List<DownloadItem> findBySourceId(UUID sourceId);

    void deleteBySourceId(UUID sourceId);

    Optional<DownloadItem> findBySourceIdAndRemoteFileId(UUID sourceId, String remoteFileId);

    // A null remoteFileId marks a directly-named registration (Bulkamancer
    // standalone DMs, Wicked per-model Gumroad lines) - a clean 1:1
    // model<->link mapping the parser already fully represents, with
    // nothing to diff (see the design doc's "Handling folders that fill in
    // over time"). FolderSyncJob uses this to skip such sources entirely.
    boolean existsBySourceIdAndRemoteFileIdIsNull(UUID sourceId);

    // Candidate set for the queue tick: claimable, not waiting on a backoff
    // window, parent source still alive. The EAGER-vs-MANUAL provider policy
    // filter is applied afterwards in Java (see RunDownloadQueueTickUseCase) -
    // no FK from download_source.creator to provider_settings.provider_id to
    // join against here.
    @Query("""
            select i from DownloadItem i
            where i.status = de.codestev.patreoningest.core.acquisition.ItemStatus.PENDING
              and (i.nextAttemptAt is null or i.nextAttemptAt <= :now)
              and i.source.claimStatus = de.codestev.patreoningest.core.acquisition.ClaimStatus.CLAIMED
              and i.source.linkDead = false
            order by i.discoveredAt asc
            """)
    List<DownloadItem> findEligibleForAutomaticDownload(@Param("now") LocalDateTime now);
}
