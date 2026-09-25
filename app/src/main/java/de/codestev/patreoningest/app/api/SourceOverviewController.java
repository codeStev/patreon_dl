package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.AddManualSourceUseCase;
import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import de.codestev.patreoningest.core.acquisition.FolderLayout;
import de.codestev.patreoningest.core.acquisition.ManualSources;
import de.codestev.patreoningest.core.acquisition.MarkClaimedManuallyUseCase;
import de.codestev.patreoningest.core.acquisition.RemoveManualSourceUseCase;
import de.codestev.patreoningest.core.fulfillment.DownloadCapability;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final DownloadCapability downloadCapability;
    private final AddManualSourceUseCase addManualSourceUseCase;
    private final RemoveManualSourceUseCase removeManualSourceUseCase;
    private final ManualSources manualSources;

    public SourceOverviewController(DownloadSourceRepository downloadSourceRepository,
                                     DownloadItemRepository downloadItemRepository,
                                     MarkClaimedManuallyUseCase markClaimedManuallyUseCase,
                                     DownloadCapability downloadCapability,
                                     AddManualSourceUseCase addManualSourceUseCase,
                                     RemoveManualSourceUseCase removeManualSourceUseCase,
                                     ManualSources manualSources) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.downloadItemRepository = downloadItemRepository;
        this.markClaimedManuallyUseCase = markClaimedManuallyUseCase;
        this.downloadCapability = downloadCapability;
        this.addManualSourceUseCase = addManualSourceUseCase;
        this.removeManualSourceUseCase = removeManualSourceUseCase;
        this.manualSources = manualSources;
    }

    // A Drive link added by hand (e.g. a persistent link that keeps getting
    // new releases). The next FolderSyncJob run picks it up.
    @PostMapping
    public ResponseEntity<?> add(@RequestBody AddSourceRequest request) {
        FolderLayout layout = request.layout() != null ? request.layout() : FolderLayout.MODELS;
        AddManualSourceUseCase.Result result = addManualSourceUseCase.add(request.name(), request.url(), layout);
        return switch (result.outcome()) {
            case ADDED -> ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result.source()));
            case INVALID_NAME -> badRequest("Name must be 1-100 characters and can't contain / \\ : * ? \" < > |");
            case RESERVED_NAME -> badRequest("That name belongs to an email provider - pick another");
            case NOT_A_DRIVE_LINK -> badRequest("Not a Google Drive folder or file link");
            case COLLECTIONS_NEED_A_FOLDER -> badRequest("A single file can't hold collections - use a folder link");
            case ALREADY_EXISTS -> badRequest("This link was already added under that name");
        };
    }

    // Removes a hand-added link from the app. Files it already downloaded
    // stay on disk.
    @DeleteMapping("/{id}")
    public ResponseEntity<?> remove(@PathVariable UUID id) {
        return switch (removeManualSourceUseCase.remove(id)) {
            case REMOVED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case NOT_ADDED_MANUALLY -> badRequest("Only links added by hand can be removed");
        };
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
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
        boolean downloadable = downloadCapability.canDownload(source.getSourceType());
        List<ItemOverviewResponse> items = downloadItemRepository.findBySourceId(source.getId())
                .stream()
                .map(item -> toResponse(item, downloadable))
                .toList();

        return new SourceOverviewResponse(
                source.getId(),
                source.getCreator(),
                source.getCategory(),
                source.getMonthLabel(),
                source.getSourceType().name(),
                source.getSourceUrl(),
                source.getFolderLayout().name(),
                manualSources.isAddedManually(source),
                source.getClaimType() != null ? source.getClaimType().name() : null,
                source.getClaimStatus().name(),
                source.getClaimNote(),
                source.getClaimReceiptUrl(),
                source.isLinkDead(),
                source.getFirstSeen(),
                source.getClaimedAt(),
                items);
    }

    private static ItemOverviewResponse toResponse(DownloadItem item, boolean downloadable) {
        return new ItemOverviewResponse(
                item.getId(),
                item.getGroupName(),
                item.getModelName(),
                item.getStatus().name(),
                downloadable,
                item.getFileSizeBytes(),
                item.getRetryCount(),
                item.getLastError(),
                item.getDiscoveredAt(),
                item.getDownloadedAt());
    }
}
