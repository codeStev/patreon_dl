package de.codestev.patreoningest.core.ingestion;

import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.SourceType;

// What a CreatorMessageParser extracts from an email - not a persisted
// entity. No id, no source_id, no status: none of those exist yet at parse
// time. modelName is null when this item only registers/refreshes a
// whole-folder source (e.g. Nomnom) rather than naming a specific model -
// see "Handling folders that fill in over time" in the design doc.
public record ParsedItem(
        String creator,
        String category,
        String monthLabel,
        SourceType sourceType,
        String sourceUrl,
        ClaimType claimType,
        String modelName
) {
}
