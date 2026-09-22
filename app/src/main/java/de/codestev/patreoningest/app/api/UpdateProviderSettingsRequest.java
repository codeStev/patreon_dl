package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.DownloadPolicy;

public record UpdateProviderSettingsRequest(DownloadPolicy downloadPolicy) {
}
