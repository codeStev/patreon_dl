package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.ProviderSettings;
import de.codestev.patreoningest.core.acquisition.ProviderSettingsRepository;
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

// Per-provider EAGER/MANUAL/DISABLED policy - rows already exist thanks to
// ProviderSettingsSeeder (one per registered CreatorMessageParser, defaults
// to MANUAL); this just exposes them for editing instead of requiring a
// direct DB edit.
@RestController
@RequestMapping("/api/provider-settings")
public class ProviderSettingsController {

    private final ProviderSettingsRepository providerSettingsRepository;

    public ProviderSettingsController(ProviderSettingsRepository providerSettingsRepository) {
        this.providerSettingsRepository = providerSettingsRepository;
    }

    @GetMapping
    public List<ProviderSettingsResponse> list() {
        return providerSettingsRepository.findAll().stream()
                .map(p -> new ProviderSettingsResponse(p.getProviderId(), p.getDownloadPolicy()))
                .sorted(Comparator.comparing(ProviderSettingsResponse::providerId))
                .toList();
    }

    @PutMapping("/{providerId}")
    public ResponseEntity<?> update(@PathVariable String providerId,
                                     @RequestBody UpdateProviderSettingsRequest request) {
        if (request.downloadPolicy() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "downloadPolicy is required"));
        }

        Optional<ProviderSettings> existing = providerSettingsRepository.findById(providerId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ProviderSettings settings = existing.get();
        settings.updatePolicy(request.downloadPolicy());
        providerSettingsRepository.save(settings);
        return ResponseEntity.ok(new ProviderSettingsResponse(providerId, settings.getDownloadPolicy()));
    }
}
