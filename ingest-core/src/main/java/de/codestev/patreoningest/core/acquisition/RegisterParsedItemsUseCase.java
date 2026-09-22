package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.ingestion.ParsedItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RegisterParsedItemsUseCase {

    private final DownloadSourceRepository downloadSourceRepository;
    private final ClaimSourceUseCase claimSourceUseCase;

    public RegisterParsedItemsUseCase(DownloadSourceRepository downloadSourceRepository,
                                       ClaimSourceUseCase claimSourceUseCase) {
        this.downloadSourceRepository = downloadSourceRepository;
        this.claimSourceUseCase = claimSourceUseCase;
    }

    public void register(List<ParsedItem> items) {
        for (ParsedItem item : items) {
            registerOne(item);
        }
    }

    private void registerOne(ParsedItem item) {
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
    }
}
