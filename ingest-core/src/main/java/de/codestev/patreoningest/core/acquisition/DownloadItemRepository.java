package de.codestev.patreoningest.core.acquisition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DownloadItemRepository extends JpaRepository<DownloadItem, UUID> {

    List<DownloadItem> findBySourceId(UUID sourceId);
}
