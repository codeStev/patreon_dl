package de.codestev.patreoningest.core.acquisition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DownloadSourceRepository extends JpaRepository<DownloadSource, UUID> {

    Optional<DownloadSource> findByCreatorAndSourceUrl(String creator, String sourceUrl);
}
