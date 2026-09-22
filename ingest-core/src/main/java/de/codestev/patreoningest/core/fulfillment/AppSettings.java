package de.codestev.patreoningest.core.fulfillment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "app_settings")
public class AppSettings {

    @Id
    private long id;

    @Column(name = "max_concurrent_downloads", nullable = false)
    private int maxConcurrentDownloads;

    @Column(name = "bandwidth_limit_kbps")
    private Integer bandwidthLimitKbps;

    @Column(name = "io_nice", nullable = false)
    private boolean ioNice;

    @Column(name = "allowed_hours_start")
    private LocalTime allowedHoursStart;

    @Column(name = "allowed_hours_end")
    private LocalTime allowedHoursEnd;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected AppSettings() {
        // JPA
    }

    public AppSettings(long id, int maxConcurrentDownloads, boolean ioNice) {
        this.id = id;
        this.maxConcurrentDownloads = maxConcurrentDownloads;
        this.ioNice = ioNice;
        this.updatedAt = LocalDateTime.now();
    }

    public long getId() {
        return id;
    }

    public int getMaxConcurrentDownloads() {
        return maxConcurrentDownloads;
    }

    public Integer getBandwidthLimitKbps() {
        return bandwidthLimitKbps;
    }

    public boolean isIoNice() {
        return ioNice;
    }

    public LocalTime getAllowedHoursStart() {
        return allowedHoursStart;
    }

    public LocalTime getAllowedHoursEnd() {
        return allowedHoursEnd;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
