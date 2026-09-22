package de.codestev.patreoningest.app.api;

import java.time.LocalTime;

public record UpdateFulfillmentSettingsRequest(
        int maxConcurrentDownloads,
        Integer bandwidthLimitKbps,
        boolean ioNice,
        LocalTime allowedHoursStart,
        LocalTime allowedHoursEnd
) {
}
