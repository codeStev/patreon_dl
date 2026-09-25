package de.codestev.patreoningest.core.acquisition;

// Per-provider, independent of DownloadPolicy: whether ClaimQueueJob may
// redeem a provider's port-backed sources (e.g. Gumroad) on its own. Owning
// something and downloading it are separate decisions - e.g. auto-redeem
// Wicked's Gumroad links but only download its big Drive folder by hand.
// Implicit claims (DRIVE/MMF) need no action and ignore this.
public enum ClaimPolicy {
    AUTO,
    // Never redeemed automatically - handed straight to the operator via
    // "Needs your action" instead.
    MANUAL
}
