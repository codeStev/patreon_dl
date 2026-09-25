package de.codestev.patreoningest.core.acquisition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "provider_settings")
public class ProviderSettings {

    @Id
    @Column(name = "provider_id")
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "download_policy", nullable = false)
    private DownloadPolicy downloadPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_policy", nullable = false)
    private ClaimPolicy claimPolicy = ClaimPolicy.AUTO;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected ProviderSettings() {
        // JPA
    }

    public ProviderSettings(String providerId, DownloadPolicy downloadPolicy) {
        this.providerId = providerId;
        this.downloadPolicy = downloadPolicy;
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePolicy(DownloadPolicy downloadPolicy) {
        this.downloadPolicy = downloadPolicy;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateClaimPolicy(ClaimPolicy claimPolicy) {
        this.claimPolicy = claimPolicy;
        this.updatedAt = LocalDateTime.now();
    }

    public ClaimPolicy getClaimPolicy() {
        return claimPolicy;
    }

    public String getProviderId() {
        return providerId;
    }

    public DownloadPolicy getDownloadPolicy() {
        return downloadPolicy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
