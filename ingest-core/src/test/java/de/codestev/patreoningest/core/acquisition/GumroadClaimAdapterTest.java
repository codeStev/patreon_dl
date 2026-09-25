package de.codestev.patreoningest.core.acquisition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// The go/no-go decisions, fed with values captured from a real Wicked
// checkout (2026-09-25). The browser glue itself needs a real run against
// Gumroad - there's no faithful local stand-in for their checkout.
class GumroadClaimAdapterTest {

    @Test
    void aFullyDiscountedProductPageIsAFreeOffer() {
        assertThat(GumroadClaimAdapter.isFreeOffer("0",
                "100% off will be applied at checkout (Code 3WEAB9N)")).isTrue();
    }

    @Test
    void aNonZeroPriceOrMissingCouponBannerIsNotAFreeOffer() {
        assertThat(GumroadClaimAdapter.isFreeOffer("16.70", "")).isFalse();
        assertThat(GumroadClaimAdapter.isFreeOffer("0", "")).isFalse();
        assertThat(GumroadClaimAdapter.isFreeOffer(null, "100% off")).isFalse();
        assertThat(GumroadClaimAdapter.isFreeOffer("5", "50% off will be applied at checkout")).isFalse();
    }

    @Test
    void recognizesAZeroTotalInAnyCurrencyFormat() {
        assertThat(GumroadClaimAdapter.isZeroTotal("Total US$0")).isTrue();
        assertThat(GumroadClaimAdapter.isZeroTotal("Total\nUS$0")).isTrue();
        assertThat(GumroadClaimAdapter.isZeroTotal("Total €0,00")).isTrue();
        assertThat(GumroadClaimAdapter.isZeroTotal("Total $0.00")).isTrue();
    }

    @Test
    void theUndiscountedTotalIsNotZero() {
        assertThat(GumroadClaimAdapter.isZeroTotal("Total US$19")).isFalse();
        assertThat(GumroadClaimAdapter.isZeroTotal("Total US$10")).isFalse();
        assertThat(GumroadClaimAdapter.isZeroTotal("Total US$0.50")).isFalse();
        assertThat(GumroadClaimAdapter.isZeroTotal("Total")).isFalse();
    }

    @Test
    void submitsOnlyWithExactlyOneItemAndAnEnabledGetButton() {
        assertThat(GumroadClaimAdapter.checkoutGuardFailure(1, 1, true)).isNull();
        assertThat(GumroadClaimAdapter.checkoutGuardFailure(2, 1, true)).contains("2 items");
        assertThat(GumroadClaimAdapter.checkoutGuardFailure(1, 0, false)).contains("\"Get\"");
        assertThat(GumroadClaimAdapter.checkoutGuardFailure(1, 1, false)).contains("\"Get\"");
    }

    @Test
    void onlyAPurchasePageCountsAsAReceipt() {
        assertThat(GumroadClaimAdapter.RECEIPT_URL.matcher(
                "https://gumroad.com/d/28e59d86037e7bc736cbbcbde5f8e992").matches()).isTrue();
        assertThat(GumroadClaimAdapter.RECEIPT_URL.matcher("https://gumroad.com/checkout").matches()).isFalse();
    }
}
