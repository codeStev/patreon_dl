package de.codestev.patreoningest.app.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record ItemOverviewResponse(
        UUID id,
        String modelName,
        String status,
        Long fileSizeBytes,
        int retryCount,
        String lastError,
        LocalDateTime discoveredAt,
        LocalDateTime downloadedAt
) {
}
