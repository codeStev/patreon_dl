package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.ingestion.CreatorMessageParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Tells hand-added sources apart from email-parsed ones without a stored
// flag: parsers register sources under their own provider id, and
// AddManualSourceUseCase refuses those ids as names - so a source whose
// creator isn't a parser's provider id was added by hand.
@Component
public class ManualSources {

    private final Set<String> parserProviderIds;

    public ManualSources(List<CreatorMessageParser> parsers) {
        this.parserProviderIds = parsers.stream()
                .map(CreatorMessageParser::providerId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isReservedName(String name) {
        return parserProviderIds.contains(name);
    }

    public boolean isAddedManually(DownloadSource source) {
        return !isReservedName(source.getCreator());
    }
}
