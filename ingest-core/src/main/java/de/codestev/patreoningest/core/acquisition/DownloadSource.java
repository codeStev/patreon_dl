package de.codestev.patreoningest.core.acquisition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "download_source")
public class DownloadSource {

    public static final String ALREADY_OWNED_NOTE = "Already owned - claimed outside the app";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String creator;

    private String category;

    @Column(name = "month_label")
    private String monthLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type")
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false)
    private ClaimStatus claimStatus;

    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    @Column(name = "last_synced")
    private LocalDateTime lastSynced;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "quiet_since")
    private LocalDateTime quietSince;

    @Column(name = "link_dead", nullable = false)
    private boolean linkDead;

    @Column(name = "claim_receipt_url")
    private String claimReceiptUrl;

    // Why the last automated claim attempt didn't succeed - shown in the
    // admin UI next to a NEEDS_MANUAL source.
    @Column(name = "claim_note")
    private String claimNote;

    @Column(name = "claim_attempts", nullable = false)
    private int claimAttempts;

    @Column(name = "next_claim_attempt_at")
    private LocalDateTime nextClaimAttemptAt;

    protected DownloadSource() {
        // JPA
    }

    public DownloadSource(String creator, String category, String monthLabel,
                           SourceType sourceType, String sourceUrl, ClaimType claimType) {
        this.creator = creator;
        this.category = category;
        this.monthLabel = monthLabel;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.claimType = claimType;
        this.claimStatus = ClaimStatus.DISCOVERED;
        this.firstSeen = LocalDateTime.now();
        this.linkDead = false;
    }

    public void markClaimed() {
        markClaimed(null);
    }

    public void markClaimed(String receiptUrl) {
        this.claimStatus = ClaimStatus.CLAIMED;
        this.claimedAt = LocalDateTime.now();
        this.claimReceiptUrl = receiptUrl;
        this.claimNote = null;
        this.nextClaimAttemptAt = null;
    }

    // Claimed outside the app (no receipt of ours) - the note tells the
    // two apart in the admin UI.
    public void markClaimedAlreadyOwned() {
        markClaimed(null);
        this.claimNote = ALREADY_OWNED_NOTE;
    }

    public void markNeedsManualClaim(String reason) {
        this.claimStatus = ClaimStatus.NEEDS_MANUAL;
        this.claimNote = reason;
        this.nextClaimAttemptAt = null;
    }

    public void markClaimAttemptFailed(String reason, LocalDateTime nextAttemptAt) {
        this.claimAttempts++;
        this.claimNote = reason;
        this.nextClaimAttemptAt = nextAttemptAt;
    }

    public void markLinkDead() {
        this.linkDead = true;
    }

    public void markSynced(boolean foundNewEntries) {
        this.lastSynced = LocalDateTime.now();
        if (foundNewEntries) {
            this.quietSince = null;
        } else if (this.quietSince == null) {
            this.quietSince = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCreator() {
        return creator;
    }

    public String getCategory() {
        return category;
    }

    public String getMonthLabel() {
        return monthLabel;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public ClaimStatus getClaimStatus() {
        return claimStatus;
    }

    public LocalDateTime getFirstSeen() {
        return firstSeen;
    }

    public LocalDateTime getLastSynced() {
        return lastSynced;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public LocalDateTime getQuietSince() {
        return quietSince;
    }

    public boolean isLinkDead() {
        return linkDead;
    }

    public String getClaimReceiptUrl() {
        return claimReceiptUrl;
    }

    public String getClaimNote() {
        return claimNote;
    }

    public int getClaimAttempts() {
        return claimAttempts;
    }

    public LocalDateTime getNextClaimAttemptAt() {
        return nextClaimAttemptAt;
    }
}
