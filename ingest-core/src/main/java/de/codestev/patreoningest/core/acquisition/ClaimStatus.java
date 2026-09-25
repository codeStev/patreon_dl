package de.codestev.patreoningest.core.acquisition;

public enum ClaimStatus {
    DISCOVERED,
    // A ClaimPort couldn't finish on its own (e.g. a bot-check challenge,
    // an expired coupon, or too many failed retries) - waiting for the
    // operator to claim it by hand and mark it done in the admin UI.
    NEEDS_MANUAL,
    CLAIMED
}
