package de.codestev.patreoningest.core.acquisition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "download_item")
public class DownloadItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "source_id", nullable = false)
    private DownloadSource source;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "remote_file_id")
    private String remoteFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(name = "local_path")
    private String localPath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "discovered_at", nullable = false)
    private LocalDateTime discoveredAt;

    @Column(name = "downloaded_at")
    private LocalDateTime downloadedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    protected DownloadItem() {
        // JPA
    }

    public DownloadItem(DownloadSource source, String modelName, String remoteFileId) {
        this.source = source;
        this.modelName = modelName;
        this.remoteFileId = remoteFileId;
        this.status = ItemStatus.PENDING;
        this.retryCount = 0;
        this.discoveredAt = LocalDateTime.now();
    }

    public void markDownloaded(String localPath, long fileSizeBytes) {
        this.status = ItemStatus.DOWNLOADED;
        this.localPath = localPath;
        this.fileSizeBytes = fileSizeBytes;
        this.downloadedAt = LocalDateTime.now();
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    // Stays PENDING - the retry/backoff loop in ExecuteDownloadUseCase picks
    // it back up once nextAttemptAt passes, up to its attempt cap.
    public void recordTransientFailure(String error, LocalDateTime nextAttemptAt) {
        this.retryCount++;
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFailedPermanently(String error) {
        this.status = ItemStatus.FAILED;
        this.lastError = error;
        this.nextAttemptAt = null;
    }

    // Manual "download now" override: clears any backoff/attempt state so
    // the next queue tick treats this as a fresh attempt regardless of how
    // it previously failed.
    public void resetForManualRetry() {
        this.status = ItemStatus.PENDING;
        this.retryCount = 0;
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    public UUID getId() {
        return id;
    }

    public DownloadSource getSource() {
        return source;
    }

    public String getModelName() {
        return modelName;
    }

    public String getRemoteFileId() {
        return remoteFileId;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public String getLocalPath() {
        return localPath;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getDiscoveredAt() {
        return discoveredAt;
    }

    public LocalDateTime getDownloadedAt() {
        return downloadedAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }
}
