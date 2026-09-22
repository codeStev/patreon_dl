package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.ingestion.ParsedItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RegisterParsedItemsUseCase {

    private final DownloadSourceRepository downloadSourceRepository;
    private final DownloadItemRepository downloadItemRepository;
    private final ClaimSourceUseCase claimSourceUseCase;
    private final ProviderSettingsRepository providerSettingsRepository;

    public RegisterParsedItemsUseCase(DownloadSourceRepository downloadSourceRepository,
                                       DownloadItemRepository downloadItemRepository,
                                       ClaimSourceUseCase claimSourceUseCase,
                                       ProviderSettingsRepository providerSettingsRepository) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.downloadItemRepository = downloadItemRepository;
        this.claimSourceUseCase = claimSourceUseCase;
        this.providerSettingsRepository = providerSettingsRepository;
    }

    public void register(List<ParsedItem> items) {
        for (ParsedItem item : items) {
            registerOne(item);
        }
    }

    private void registerOne(ParsedItem item) {
        boolean disabled = providerSettingsRepository.findById(item.creator())
                .map(settings -> settings.getDownloadPolicy() == DownloadPolicy.DISABLED)
                .orElse(false);
        if (disabled) {
            // "Don't even claim; ignore the provider entirely" - no source,
            // no item, nothing shows up anywhere for a disabled provider.
            return;
        }

        Optional<DownloadSource> existing = downloadSourceRepository.findByCreatorAndSourceUrl(
                item.creator(), item.sourceUrl());

        if (existing.isPresent()) {
            // Re-announcement of the same link (e.g. a living Nomnom
            // folder mentioned again in a later email) - dedup key is
            // (creator, source_url), not the message. No-op, not a
            // duplicate row.
            return;
        }

        DownloadSource source = new DownloadSource(
                item.creator(), item.category(), item.monthLabel(),
                item.sourceType(), item.sourceUrl(), item.claimType());
        downloadSourceRepository.save(source);
        claimSourceUseCase.claim(source);

        if (item.modelName() != null) {
            // A clean 1:1 model<->link mapping (Bulkamancer's standalone
            // DMs, Wicked's per-model Gumroad lines) - the parser already
            // knows the exact model, so the item is created directly
            // rather than waiting on a folder-diffing job that has nothing
            // to diff here. remote_file_id stays null - unknown until
            // actually downloaded.
            downloadItemRepository.save(new DownloadItem(source, item.modelName(), null));
        }
    }
}
