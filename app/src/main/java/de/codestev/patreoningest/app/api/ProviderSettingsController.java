package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.ProviderSettings;
import de.codestev.patreoningest.core.acquisition.ProviderSettingsRepository;
import de.codestev.patreoningest.core.ingestion.CreatorMessageParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Per-provider EAGER/MANUAL/DISABLED download policy plus the independent
// AUTO/MANUAL claim (redeem) policy - rows already exist thanks to
// ProviderSettingsSeeder (one per registered CreatorMessageParser, defaults
// to MANUAL); this just exposes them for editing instead of requiring a
// direct DB edit.
@RestController
@RequestMapping("/api/provider-settings")
public class ProviderSettingsController {

    private final ProviderSettingsRepository providerSettingsRepository;
    private final Set<String> providersWithRedeemableLinks;

    public ProviderSettingsController(ProviderSettingsRepository providerSettingsRepository,
                                       List<CreatorMessageParser> parsers) {
        this.providerSettingsRepository = providerSettingsRepository;
        this.providersWithRedeemableLinks = parsers.stream()
                .filter(CreatorMessageParser::hasRedeemableLinks)
                .map(CreatorMessageParser::providerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @GetMapping
    public List<ProviderSettingsResponse> list() {
        return providerSettingsRepository.findAll().stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(ProviderSettingsResponse::providerId))
                .toList();
    }

    @PutMapping("/{providerId}")
    public ResponseEntity<?> update(@PathVariable String providerId,
                                     @RequestBody UpdateProviderSettingsRequest request) {
        if (request.downloadPolicy() == null && request.claimPolicy() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "downloadPolicy or claimPolicy is required"));
        }

        Optional<ProviderSettings> existing = providerSettingsRepository.findById(providerId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ProviderSettings settings = existing.get();
        if (request.downloadPolicy() != null) {
            settings.updatePolicy(request.downloadPolicy());
        }
        if (request.claimPolicy() != null) {
            settings.updateClaimPolicy(request.claimPolicy());
        }
        providerSettingsRepository.save(settings);
        return ResponseEntity.ok(toResponse(settings));
    }

    private ProviderSettingsResponse toResponse(ProviderSettings settings) {
        return ProviderSettingsResponse.from(settings,
                providersWithRedeemableLinks.contains(settings.getProviderId()));
    }
}
