package de.codestev.patreoningest.core.acquisition;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

// Claims a free (100%-off coupon) Gumroad product by driving the real
// checkout in headless Chromium. Flow and selectors were captured from a
// real Wicked redemption - see the design doc's GumroadClaimAdapter
// section. Every class on Gumroad's pages is a Tailwind utility, so
// everything here matches on role/label/text/itemprop instead.
//
// Safety rule: this must never pay for anything. Every guard below aborts
// before the submit click unless the page proves the checkout is free.
//
// Checkout is protected by reCAPTCHA Enterprise. If it shows a challenge,
// this hands the source to the operator (NEEDS_MANUAL) - it deliberately
// makes no attempt to solve or evade it.
//
// Only active when patreon.acquisition.gumroad.email is set to something
// non-blank - same reasoning as ImapMailboxAdapter's custom condition.
@Component
@Conditional(GumroadClaimAdapter.GumroadEmailConfigured.class)
public class GumroadClaimAdapter implements ClaimPort {

    private static final Logger log = LoggerFactory.getLogger(GumroadClaimAdapter.class);

    private static final double STEP_TIMEOUT_MS = 30_000;
    // The coupon lands a moment after the checkout page loads - wait on
    // the total actually becoming zero, never a fixed sleep.
    private static final double DISCOUNT_TIMEOUT_MS = 15_000;
    private static final double SUBMIT_TIMEOUT_MS = 60_000;

    private static final Pattern CHECKOUT_URL = Pattern.compile("https://gumroad\\.com/checkout.*");
    static final Pattern RECEIPT_URL = Pattern.compile("https://gumroad\\.com/d/[0-9a-f]{32}.*");
    private static final Pattern ZERO_AMOUNT = Pattern.compile(".*[^\\d.,]0(?:[.,]00?)?$");

    private final GumroadProperties properties;

    public GumroadClaimAdapter(GumroadProperties properties) {
        this.properties = properties;
    }

    @Override
    public SourceType supports() {
        return SourceType.GUMROAD;
    }

    @Override
    public ClaimOutcome claim(DownloadSource source) {
        // A fresh browser (and so an empty cart) per claim - nothing from an
        // earlier attempt can end up in this checkout.
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                     new BrowserType.LaunchOptions().setHeadless(properties.headless()))) {
            Page page = browser.newContext().newPage();
            page.setDefaultTimeout(STEP_TIMEOUT_MS);
            return runCheckout(page, source.getSourceUrl());
        }
    }

    private ClaimOutcome runCheckout(Page page, String productUrl) {
        page.navigate(productUrl);

        // 1. Pre-check on the product page: the coupon from the URL must
        // already show as 100% off, otherwise it has expired or is invalid.
        String price = page.locator("[itemprop=price]").first().getAttribute("content");
        Locator status = page.locator("[role=status]");
        String statusText = status.count() > 0 ? status.first().innerText() : "";
        if (!isFreeOffer(price, statusText)) {
            return new ClaimOutcome.NeedsManual("Offer is not free any more (price " + price
                    + ") - the coupon has probably expired");
        }

        // 2. "I want this!" is a link, present twice on the page.
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("I want this!")).first().click();
        page.waitForURL(CHECKOUT_URL);

        // 3. Wait for the discount to land on the checkout total.
        Locator totalRow = page.locator("h4", new Page.LocatorOptions().setHasText(Pattern.compile("^Total$")))
                .first().locator("xpath=..");
        try {
            page.waitForCondition(() -> isZeroTotal(totalRow.innerText()),
                    new Page.WaitForConditionOptions().setTimeout(DISCOUNT_TIMEOUT_MS));
        } catch (TimeoutError e) {
            return new ClaimOutcome.RetryLater("Discount did not apply at checkout in time (total: "
                    + totalRow.innerText().replaceAll("\\s+", " ") + ")");
        }

        // 4. Guards, only once the discount has landed.
        int lineItems = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Remove").setExact(true)).count();
        Locator getButton = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Get").setExact(true));
        String guardFailure = checkoutGuardFailure(lineItems, getButton.count(), getButton.count() == 1
                && getButton.isEnabled());
        if (guardFailure != null) {
            return new ClaimOutcome.NeedsManual(guardFailure);
        }

        // 5. Submit.
        page.getByLabel("Email address").fill(properties.email());
        getButton.click();

        // 6. The purchase page, an "already own this" dialog (the email has
        // bought it before - Gumroad only knows once the email is submitted,
        // nothing on the earlier pages hints at it), or a bot-check challenge.
        // The dialog offers "Buy again" - never click that.
        Locator alreadyOwned = page.getByRole(AriaRole.DIALOG,
                new Page.GetByRoleOptions().setName("You already own this"));
        Locator challenge = page.locator("iframe[src*='recaptcha'][src*='bframe']");
        try {
            page.waitForCondition(
                    () -> RECEIPT_URL.matcher(page.url()).matches()
                            || alreadyOwned.isVisible()
                            || isOnScreen(page, challenge),
                    new Page.WaitForConditionOptions().setTimeout(SUBMIT_TIMEOUT_MS));
        } catch (TimeoutError e) {
            return new ClaimOutcome.RetryLater("No purchase page after submitting checkout" + alertSuffix(page));
        }

        if (RECEIPT_URL.matcher(page.url()).matches()) {
            log.info("Gumroad claim succeeded: {}", page.locator("h1").first().innerText());
            return new ClaimOutcome.Claimed(page.url());
        }
        if (alreadyOwned.isVisible()) {
            return new ClaimOutcome.AlreadyOwned();
        }
        return new ClaimOutcome.NeedsManual("Gumroad showed a reCAPTCHA challenge - claim it by hand");
    }

    static boolean isFreeOffer(String priceContent, String statusText) {
        return "0".equals(priceContent)
                && statusText != null
                && statusText.toLowerCase(Locale.ROOT).contains("100% off");
    }

    static boolean isZeroTotal(String totalRowText) {
        return totalRowText != null
                && ZERO_AMOUNT.matcher(totalRowText.replaceAll("\\s+", " ").trim()).matches();
    }

    // null = safe to submit; otherwise why not.
    static String checkoutGuardFailure(int lineItems, int getButtons, boolean getButtonEnabled) {
        if (lineItems != 1) {
            return "Checkout cart holds " + lineItems + " items instead of exactly 1 - refusing to submit";
        }
        if (getButtons != 1 || !getButtonEnabled) {
            return "Checkout has no enabled \"Get\" button (a paid checkout says \"Pay\") - refusing to submit";
        }
        return null;
    }

    // reCAPTCHA keeps its challenge frame in the DOM before it challenges -
    // sometimes parked off-screen, sometimes (seen in a real checkout) laid
    // out across the top of the viewport but visibility:hidden. Neither
    // presence nor position alone is the signal; isVisible() respects
    // visibility:hidden, and the box check rules out off-screen parking.
    private static boolean isOnScreen(Page page, Locator frame) {
        if (frame.count() == 0 || !frame.first().isVisible()) {
            return false;
        }
        BoundingBox box = frame.first().boundingBox();
        return box != null && box.width > 50 && box.height > 50
                && box.x >= 0 && box.y >= 0
                && box.y < page.viewportSize().height;
    }

    private static String alertSuffix(Page page) {
        Locator alerts = page.locator("[role=alert]");
        for (int i = 0; i < alerts.count(); i++) {
            String text = alerts.nth(i).innerText().trim();
            if (!text.isEmpty()) {
                return " (page says: " + text + ")";
            }
        }
        return "";
    }

    static class GumroadEmailConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("patreon.acquisition.gumroad.email"));
        }
    }
}
