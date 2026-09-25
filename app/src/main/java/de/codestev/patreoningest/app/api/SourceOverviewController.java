package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import de.codestev.patreoningest.core.acquisition.MarkClaimedManuallyUseCase;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Admin API "driving adapter" (see design doc's hexagonal section) - reads
// across Acquisition's own repositories to compose one view for the admin
// UI. Not paginated: a personal ingest tool's source count is expected to
// stay small enough that this is a non-issue; revisit if that stops being
// true.
@RestController
@RequestMapping("/api/sources")
public class SourceOverviewController {

    private final DownloadSourceRepository downloadSourceRepository;
    private final DownloadItemRepository downloadItemRepository;
    private final MarkClaimedManuallyUseCase markClaimedManuallyUseCase;

    public SourceOverviewController(DownloadSourceRepository downloadSourceRepository,
                                     DownloadItemRepository downloadItemRepository,
                                     MarkClaimedManuallyUseCase markClaimedManuallyUseCase) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.downloadItemRepository = downloadItemRepository;
        this.markClaimedManuallyUseCase = markClaimedManuallyUseCase;
    }

    // The operator claimed this source by hand (e.g. after a bot-check
    // challenge stopped GumroadClaimAdapter) and confirms it here.
    @PostMapping("/{id}/mark-claimed")
    public ResponseEntity<?> markClaimed(@PathVariable UUID id) {
        return switch (markClaimedManuallyUseCase.markClaimed(id)) {
            case MARKED_CLAIMED -> ResponseEntity.ok(Map.of("status", "CLAIMED"));
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case ALREADY_CLAIMED -> ResponseEntity.badRequest().body(Map.of("message", "Source is already claimed"));
        };
    }

    @GetMapping
    public List<SourceOverviewResponse> list() {
        return downloadSourceRepository.findAll(Sort.by(Sort.Direction.DESC, "firstSeen"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private SourceOverviewResponse toResponse(DownloadSource source) {
        List<ItemOverviewResponse> items = downloadItemRepository.findBySourceId(source.getId())
                .stream()
                .map(SourceOverviewController::toResponse)
                .toList();

        return new SourceOverviewResponse(
                source.getId(),
                source.getCreator(),
                source.getCategory(),
                source.getMonthLabel(),
                source.getSourceType().name(),
                source.getSourceUrl(),
                source.getClaimType() != null ? source.getClaimType().name() : null,
                source.getClaimStatus().name(),
                source.getClaimNote(),
                source.getClaimReceiptUrl(),
                source.isLinkDead(),
                source.getFirstSeen(),
                source.getClaimedAt(),
                items);
    }

    private static ItemOverviewResponse toResponse(DownloadItem item) {
        return new ItemOverviewResponse(
                item.getId(),
                item.getModelName(),
                item.getStatus().name(),
                item.getFileSizeBytes(),
                item.getRetryCount(),
                item.getLastError(),
                item.getDiscoveredAt(),
                item.getDownloadedAt());
    }
}
