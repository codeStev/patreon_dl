package de.codestev.patreoningest.core.acquisition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DownloadSourceRepository extends JpaRepository<DownloadSource, UUID> {

    Optional<DownloadSource> findByCreatorAndSourceUrl(String creator, String sourceUrl);

    List<DownloadSource> findBySourceTypeAndClaimStatusAndLinkDead(
            SourceType sourceType, ClaimStatus claimStatus, boolean linkDead);

    // Port-backed claims whose next attempt is due - oldest first, so a
    // backlog drains in discovery order.
    @Query("""
            select s from DownloadSource s
            where s.claimStatus = de.codestev.patreoningest.core.acquisition.ClaimStatus.DISCOVERED
              and s.claimType is not null
              and s.claimType <> de.codestev.patreoningest.core.acquisition.ClaimType.NONE
              and s.linkDead = false
              and (s.nextClaimAttemptAt is null or s.nextClaimAttemptAt <= :now)
            order by s.firstSeen
            """)
    List<DownloadSource> findClaimsDue(@Param("now") LocalDateTime now);

    List<DownloadSource> findByClaimStatusOrderByFirstSeen(ClaimStatus claimStatus);
}
