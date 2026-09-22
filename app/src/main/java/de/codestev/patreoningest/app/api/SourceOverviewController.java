package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    public SourceOverviewController(DownloadSourceRepository downloadSourceRepository,
                                     DownloadItemRepository downloadItemRepository) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.downloadItemRepository = downloadItemRepository;
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
