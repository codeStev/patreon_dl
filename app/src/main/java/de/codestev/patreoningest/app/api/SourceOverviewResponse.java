package de.codestev.patreoningest.app.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SourceOverviewResponse(
        UUID id,
        String creator,
        String category,
        String monthLabel,
        String sourceType,
        String sourceUrl,
        String claimType,
        String claimStatus,
        boolean linkDead,
        LocalDateTime firstSeen,
        LocalDateTime claimedAt,
        List<ItemOverviewResponse> items
) {
}
