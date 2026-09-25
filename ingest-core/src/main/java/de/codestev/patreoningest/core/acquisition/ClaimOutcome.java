package de.codestev.patreoningest.core.acquisition;

// What a ClaimPort reports back. Deliberately three-way rather than
// success/exception: "try again later" (flaky page, coupon not applied in
// time) and "a human has to do this" (bot-check challenge, expired coupon)
// need different handling, and neither is a crash.
public sealed interface ClaimOutcome {

    // receiptUrl is optional - e.g. Gumroad's per-purchase /d/<id> page.
    record Claimed(String receiptUrl) implements ClaimOutcome {
    }

    // The target says this account already owns it (e.g. the operator
    // redeemed it by hand before the app got to it) - as good as claimed.
    record AlreadyOwned() implements ClaimOutcome {
    }

    record NeedsManual(String reason) implements ClaimOutcome {
    }

    record RetryLater(String reason) implements ClaimOutcome {
    }
}
