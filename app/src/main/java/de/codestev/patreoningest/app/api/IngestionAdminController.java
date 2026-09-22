package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.ingestion.ResetIngestTrackingUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionAdminController {

    private final ResetIngestTrackingUseCase resetIngestTrackingUseCase;

    public IngestionAdminController(ResetIngestTrackingUseCase resetIngestTrackingUseCase) {
        this.resetIngestTrackingUseCase = resetIngestTrackingUseCase;
    }

    @PostMapping("/reset-tracking")
    public ResponseEntity<Void> resetTracking() {
        resetIngestTrackingUseCase.reset();
        return ResponseEntity.accepted().build();
    }
}
