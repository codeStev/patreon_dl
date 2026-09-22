package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.fulfillment.TriggerManualDownloadUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/download-items")
public class DownloadItemController {

    private final TriggerManualDownloadUseCase triggerManualDownloadUseCase;

    public DownloadItemController(TriggerManualDownloadUseCase triggerManualDownloadUseCase) {
        this.triggerManualDownloadUseCase = triggerManualDownloadUseCase;
    }

    @PostMapping("/{id}/download-now")
    public ResponseEntity<?> downloadNow(@PathVariable UUID id) {
        TriggerManualDownloadUseCase.Result result = triggerManualDownloadUseCase.trigger(id);
        return switch (result) {
            case DISPATCHED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "DISPATCHED"));
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case ALREADY_DOWNLOADED -> badRequest("Item is already downloaded");
            case NOT_CLAIMED -> badRequest("Item's source is not claimed yet");
            case LINK_DEAD -> badRequest("Item's source is flagged link-dead");
            case CONCURRENCY_LIMIT_REACHED -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Max concurrent downloads reached - try again shortly"));
        };
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
