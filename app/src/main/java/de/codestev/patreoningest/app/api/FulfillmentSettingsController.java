package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.fulfillment.AppSettings;
import de.codestev.patreoningest.core.fulfillment.AppSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fulfillment-settings")
public class FulfillmentSettingsController {

    private final AppSettingsRepository appSettingsRepository;

    public FulfillmentSettingsController(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    @GetMapping
    public FulfillmentSettingsResponse get() {
        return toResponse(currentSettings());
    }

    @PutMapping
    public ResponseEntity<?> update(@RequestBody UpdateFulfillmentSettingsRequest request) {
        if (request.maxConcurrentDownloads() < 1) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "maxConcurrentDownloads must be at least 1"));
        }
        if (request.bandwidthLimitKbps() != null && request.bandwidthLimitKbps() < 1) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "bandwidthLimitKbps must be positive when set"));
        }
        if ((request.allowedHoursStart() == null) != (request.allowedHoursEnd() == null)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "allowedHoursStart and allowedHoursEnd must both be set or both be empty"));
        }

        AppSettings settings = currentSettings();
        settings.update(request.maxConcurrentDownloads(), request.bandwidthLimitKbps(), request.ioNice(),
                request.allowedHoursStart(), request.allowedHoursEnd());
        appSettingsRepository.save(settings);
        return ResponseEntity.ok(toResponse(settings));
    }

    private AppSettings currentSettings() {
        return appSettingsRepository.findById(1L)
                .orElseGet(() -> appSettingsRepository.save(new AppSettings(1L, 1, true)));
    }

    private static FulfillmentSettingsResponse toResponse(AppSettings settings) {
        return new FulfillmentSettingsResponse(
                settings.getMaxConcurrentDownloads(), settings.getBandwidthLimitKbps(), settings.isIoNice(),
                settings.getAllowedHoursStart(), settings.getAllowedHoursEnd());
    }
}
