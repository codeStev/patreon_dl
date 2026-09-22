package de.codestev.patreoningest.app.api;

import java.time.LocalTime;

// ioNice/renameSpacesToUnderscores are boxed Boolean, not primitive: Jackson
// 3's record deserialization throws (HttpMessageNotReadableException)
// rather than defaulting to false when a primitive boolean creator
// parameter is simply absent from the JSON body - unlike Integer/LocalTime,
// which already bind to null fine when missing. Verified directly (a test
// omitting renameSpacesToUnderscores as a primitive failed deserialization
// entirely), not assumed from Jackson 2 muscle memory.
public record UpdateFulfillmentSettingsRequest(
        int maxConcurrentDownloads,
        Integer bandwidthLimitKbps,
        Boolean ioNice,
        LocalTime allowedHoursStart,
        LocalTime allowedHoursEnd,
        Boolean renameSpacesToUnderscores
) {
}
