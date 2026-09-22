package de.codestev.patreoningest.app.api;

import java.time.LocalTime;

public record FulfillmentSettingsResponse(
        int maxConcurrentDownloads,
        Integer bandwidthLimitKbps,
        boolean ioNice,
        LocalTime allowedHoursStart,
        LocalTime allowedHoursEnd,
        boolean renameSpacesToUnderscores
) {
}
