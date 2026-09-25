package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.ClaimPolicy;
import de.codestev.patreoningest.core.acquisition.DownloadPolicy;
import de.codestev.patreoningest.core.acquisition.ProviderSettings;

// hasRedeemableLinks: whether claimPolicy means anything for this provider
// (see CreatorMessageParser#hasRedeemableLinks) - the UI hides it otherwise.
public record ProviderSettingsResponse(String providerId, DownloadPolicy downloadPolicy,
                                       ClaimPolicy claimPolicy, boolean hasRedeemableLinks) {

    static ProviderSettingsResponse from(ProviderSettings settings, boolean hasRedeemableLinks) {
        return new ProviderSettingsResponse(settings.getProviderId(), settings.getDownloadPolicy(),
                settings.getClaimPolicy(), hasRedeemableLinks);
    }
}
