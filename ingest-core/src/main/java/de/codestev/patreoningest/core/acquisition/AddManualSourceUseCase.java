package de.codestev.patreoningest.core.acquisition;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

// The operator adds a Drive link by hand in the admin UI - typically a
// persistent link that someone keeps filling with new releases, which no
// email ever announces. The name the operator gives it takes the place of
// a parser's provider id: it gets its own provider_settings row (and so
// its own download policy) and becomes the link's top-level download
// folder. FolderSyncJob then syncs it like any other claimed Drive source.
@Component
public class AddManualSourceUseCase {

    public enum Outcome {
        ADDED, INVALID_NAME, RESERVED_NAME, NOT_A_DRIVE_LINK, COLLECTIONS_NEED_A_FOLDER, ALREADY_EXISTS
    }

    public record Result(Outcome outcome, DownloadSource source) {
    }

    // The name becomes a directory - validated rather than silently
    // sanitized, so the folder on disk is exactly what the operator typed.
    private static final Pattern UNSAFE_NAME_CHARS = Pattern.compile("[/\\\\:*?\"<>|]");
    private static final int MAX_NAME_LENGTH = 100;

    private final DownloadSourceRepository downloadSourceRepository;
    private final ProviderSettingsRepository providerSettingsRepository;
    private final ClaimSourceUseCase claimSourceUseCase;
    private final ManualSources manualSources;

    public AddManualSourceUseCase(DownloadSourceRepository downloadSourceRepository,
                                  ProviderSettingsRepository providerSettingsRepository,
                                  ClaimSourceUseCase claimSourceUseCase,
                                  ManualSources manualSources) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.providerSettingsRepository = providerSettingsRepository;
        this.claimSourceUseCase = claimSourceUseCase;
        this.manualSources = manualSources;
    }

    @Transactional
    public Result add(String rawName, String rawUrl, FolderLayout layout) {
        String name = rawName == null ? "" : rawName.strip();
        String url = rawUrl == null ? "" : rawUrl.strip();

        if (!isValidName(name)) {
            return rejected(Outcome.INVALID_NAME);
        }
        // Sharing a parser's name would silently merge this link into that
        // provider's policy and download folder - and is what lets
        // ManualSources tell hand-added sources apart.
        if (manualSources.isReservedName(name)) {
            return rejected(Outcome.RESERVED_NAME);
        }
        Optional<String> folderId = GoogleDriveUrls.tryExtractFolderId(url);
        if (folderId.isEmpty() && GoogleDriveUrls.tryExtractFileId(url).isEmpty()) {
            return rejected(Outcome.NOT_A_DRIVE_LINK);
        }
        if (folderId.isEmpty() && layout == FolderLayout.COLLECTIONS) {
            return rejected(Outcome.COLLECTIONS_NEED_A_FOLDER);
        }
        if (downloadSourceRepository.findByCreatorAndSourceUrl(name, url).isPresent()) {
            return rejected(Outcome.ALREADY_EXISTS);
        }

        if (providerSettingsRepository.findById(name).isEmpty()) {
            // Same default as ProviderSettingsSeeder - nothing downloads
            // until the operator opts in.
            providerSettingsRepository.save(new ProviderSettings(name, DownloadPolicy.MANUAL));
        }
        DownloadSource source = downloadSourceRepository.save(new DownloadSource(
                name, null, null, SourceType.DRIVE, url, ClaimType.NONE, layout));
        claimSourceUseCase.claim(source);
        return new Result(Outcome.ADDED, source);
    }

    private static boolean isValidName(String name) {
        return !name.isEmpty()
                && name.length() <= MAX_NAME_LENGTH
                && !name.equals(".") && !name.equals("..")
                && !UNSAFE_NAME_CHARS.matcher(name).find();
    }

    private static Result rejected(Outcome outcome) {
        return new Result(outcome, null);
    }
}
