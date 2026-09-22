package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.ingestion.DynamicPollTrigger;
import de.codestev.patreoningest.core.ingestion.IngestionSettings;
import de.codestev.patreoningest.core.ingestion.IngestionSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Admin API "driving adapter" (see design doc's hexagonal section) - this
// isn't a fourth bounded context, just a thin REST wrapper composing
// Ingestion's own settings repository for the (eventual) admin UI.
@RestController
@RequestMapping("/api/ingestion-settings")
public class IngestionSettingsController {

    static final int MIN_POLL_INTERVAL_SECONDS = 60;

    private final IngestionSettingsRepository ingestionSettingsRepository;

    public IngestionSettingsController(IngestionSettingsRepository ingestionSettingsRepository) {
        this.ingestionSettingsRepository = ingestionSettingsRepository;
    }

    @GetMapping
    public IngestionSettingsResponse get() {
        return toResponse(currentSettings());
    }

    @PutMapping
    public ResponseEntity<?> update(@RequestBody UpdateIngestionSettingsRequest request) {
        if (request.pollIntervalSeconds() < MIN_POLL_INTERVAL_SECONDS) {
            // Built explicitly rather than thrown as a ResponseStatusException:
            // Boot's default error body drops the reason text even with
            // server.error.include-message=always for exceptions raised
            // from within a controller method - this guarantees the
            // frontend actually gets a usable message.
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "pollIntervalSeconds must be at least " + MIN_POLL_INTERVAL_SECONDS));
        }

        IngestionSettings settings = currentSettings();
        settings.setPollIntervalSeconds(request.pollIntervalSeconds());
        ingestionSettingsRepository.save(settings);
        return ResponseEntity.ok(toResponse(settings));
    }

    private IngestionSettings currentSettings() {
        return ingestionSettingsRepository.findById(1L)
                .orElseGet(() -> ingestionSettingsRepository.save(
                        new IngestionSettings(1L, DynamicPollTrigger.DEFAULT_INTERVAL_SECONDS)));
    }

    private static IngestionSettingsResponse toResponse(IngestionSettings settings) {
        return new IngestionSettingsResponse(settings.getPollIntervalSeconds());
    }
}
