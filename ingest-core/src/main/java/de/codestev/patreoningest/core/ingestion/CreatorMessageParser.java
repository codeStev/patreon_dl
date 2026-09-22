package de.codestev.patreoningest.core.ingestion;

import java.time.LocalDate;
import java.util.List;

public interface CreatorMessageParser {

    // Stable id for this provider - matches provider_settings.provider_id
    // and ParsedItem.creator(). Used to seed a default provider_settings
    // row for every registered parser on startup (see
    // ProviderSettingsSeeder), so "add a provider" never requires a manual
    // settings-table edit.
    String providerId();

    boolean supports(String fromAddress, String subject);

    List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt);
}
