package de.codestev.patreoningest.core.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_settings")
public class IngestionSettings {

    @Id
    private long id;

    @Column(name = "poll_interval_seconds", nullable = false)
    private int pollIntervalSeconds;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected IngestionSettings() {
        // JPA
    }

    public IngestionSettings(long id, int pollIntervalSeconds) {
        this.id = id;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.updatedAt = LocalDateTime.now();
    }

    public long getId() {
        return id;
    }

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public void setPollIntervalSeconds(int pollIntervalSeconds) {
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
