package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.ClaimPolicy;
import de.codestev.patreoningest.core.acquisition.DownloadPolicy;

// Either field may be omitted to leave that setting unchanged - but not both.
public record UpdateProviderSettingsRequest(DownloadPolicy downloadPolicy, ClaimPolicy claimPolicy) {
}
