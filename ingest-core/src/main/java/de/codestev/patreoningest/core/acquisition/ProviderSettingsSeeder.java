package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.ingestion.CreatorMessageParser;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// Every registered parser bean gets a default provider_settings row
// (default MANUAL, per the design doc) if one doesn't already exist -
// "add a provider" stays a one-class change, the settings row appears
// automatically instead of needing a manual DB edit. Never overwrites an
// existing row - an operator's chosen policy must survive every restart.
@Component
public class ProviderSettingsSeeder implements ApplicationRunner {

    private final List<CreatorMessageParser> parsers;
    private final ProviderSettingsRepository providerSettingsRepository;

    public ProviderSettingsSeeder(List<CreatorMessageParser> parsers,
                                   ProviderSettingsRepository providerSettingsRepository) {
        this.parsers = parsers;
        this.providerSettingsRepository = providerSettingsRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (CreatorMessageParser parser : parsers) {
            String providerId = parser.providerId();
            if (providerSettingsRepository.findById(providerId).isEmpty()) {
                providerSettingsRepository.save(new ProviderSettings(providerId, DownloadPolicy.MANUAL));
            }
        }
    }
}
