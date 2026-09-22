package de.codestev.patreoningest.core.acquisition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DownloadSourceRepository extends JpaRepository<DownloadSource, UUID> {

    Optional<DownloadSource> findByCreatorAndSourceUrl(String creator, String sourceUrl);

    List<DownloadSource> findBySourceTypeAndClaimStatusAndLinkDead(
            SourceType sourceType, ClaimStatus claimStatus, boolean linkDead);
}
