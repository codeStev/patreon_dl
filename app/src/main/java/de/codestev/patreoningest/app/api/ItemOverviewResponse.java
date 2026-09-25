package de.codestev.patreoningest.app.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record ItemOverviewResponse(
        UUID id,
        // The top-level folder (usually the creator) a collection came
        // from - null except for items of COLLECTIONS sources.
        String groupName,
        String modelName,
        String status,
        // false for claim-only / manual-retrieval items (e.g. Gumroad, MMF) -
        // no SourceDownloader exists for them, so no "Download now".
        boolean downloadable,
        Long fileSizeBytes,
        int retryCount,
        String lastError,
        LocalDateTime discoveredAt,
        LocalDateTime downloadedAt
) {
}
