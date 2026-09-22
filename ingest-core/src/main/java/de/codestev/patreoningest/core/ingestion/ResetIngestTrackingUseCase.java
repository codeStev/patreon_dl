package de.codestev.patreoningest.core.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Admin-triggered escape hatch for when a parser was fixed/changed after
// already having scanned the mailbox once - the UID watermark from a
// previous scan would otherwise permanently prevent those same messages
// from ever being looked at again (see the incident this was built for:
// an early parser rewrite meant already-processed emails could never be
// re-parsed without manually truncating processed_email via psql).
//
// Deliberately only clears processed_email, not download_source/
// download_item - re-registering an already-known (creator, source_url) is
// a no-op per RegisterParsedItemsUseCase's dedup rule, so a full mailbox
// rescan is safe and won't touch existing claim/download state. The
// operator's download history (what's already been downloaded) is exactly
// the information that must survive a reset.
@Component
public class ResetIngestTrackingUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResetIngestTrackingUseCase.class);

    private final ProcessedEmailRepository processedEmailRepository;

    public ResetIngestTrackingUseCase(ProcessedEmailRepository processedEmailRepository) {
        this.processedEmailRepository = processedEmailRepository;
    }

    public void reset() {
        long count = processedEmailRepository.count();
        processedEmailRepository.deleteAll();
        log.warn("Ingest tracking reset - cleared {} processed_email row(s); next poll will rescan the whole mailbox", count);
    }
}
