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

    @Column(name = "rename_spaces_to_underscores", nullable = false)
    private boolean renameSpacesToUnderscores;

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

    public void update(int maxConcurrentDownloads, Integer bandwidthLimitKbps, boolean ioNice,
                        LocalTime allowedHoursStart, LocalTime allowedHoursEnd,
                        boolean renameSpacesToUnderscores) {
        this.maxConcurrentDownloads = maxConcurrentDownloads;
        this.bandwidthLimitKbps = bandwidthLimitKbps;
        this.ioNice = ioNice;
        this.allowedHoursStart = allowedHoursStart;
        this.allowedHoursEnd = allowedHoursEnd;
        this.renameSpacesToUnderscores = renameSpacesToUnderscores;
        this.updatedAt = LocalDateTime.now();
    }

    // True when the given time falls inside the configured allowed-hours
    // window (correctly handling a window that crosses midnight, e.g.
    // 22:00-06:00). No window configured at all means always allowed.
    public boolean allowsDownloadsAt(LocalTime time) {
        if (allowedHoursStart == null || allowedHoursEnd == null) {
            return true;
        }
        if (allowedHoursStart.isBefore(allowedHoursEnd)) {
            return !time.isBefore(allowedHoursStart) && time.isBefore(allowedHoursEnd);
        }
        return !time.isBefore(allowedHoursStart) || time.isBefore(allowedHoursEnd);
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

    public boolean isRenameSpacesToUnderscores() {
        return renameSpacesToUnderscores;
    }
}
