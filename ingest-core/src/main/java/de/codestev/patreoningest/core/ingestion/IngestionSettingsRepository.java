package de.codestev.patreoningest.core.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionSettingsRepository extends JpaRepository<IngestionSettings, Long> {
}
