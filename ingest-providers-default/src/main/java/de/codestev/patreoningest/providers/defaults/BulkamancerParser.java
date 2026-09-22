package de.codestev.patreoningest.providers.defaults;

import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.SourceType;
import de.codestev.patreoningest.core.ingestion.CreatorMessageParser;
import de.codestev.patreoningest.core.ingestion.ParsedItem;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bulkamancer's standalone single-model DMs (Patreon's own DM-notification
 * template, sent from no-reply@community.patreon.com - matching happens on
 * subject, not from-address): {@code Name: <drive-url>} on one line. Unlike
 * Nomnom's folders, this is an explicit, deliberate 1:1 pairing typed by
 * the creator for one specific model - the design doc calls this out as a
 * clean 1:1 model-to-link mapping, so the name is trusted here.
 *
 * <p>The recap/loyalty sections pointing at a MyMiniFactory shared library
 * (per the design doc) are a different sub-format with no confirmed real
 * sample yet - not implemented until one exists.
 */
@Component
public class BulkamancerParser implements CreatorMessageParser {

    private static final Pattern NAMED_DRIVE_LINK =
            Pattern.compile("^(.+?):\\s*(https?://drive\\.google\\.com/\\S+)$");

    @Override
    public String providerId() {
        return "bulkamancer";
    }

    @Override
    public boolean supports(String fromAddress, String subject) {
        return subject != null && subject.toLowerCase(Locale.ROOT).contains("bulkamancer");
    }

    @Override
    public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
        List<ParsedItem> items = new ArrayList<>();

        for (String rawLine : plainTextBody.split("\\R")) {
            String line = rawLine.trim();
            Matcher matcher = NAMED_DRIVE_LINK.matcher(line);
            if (matcher.matches()) {
                items.add(new ParsedItem(providerId(), null, null, SourceType.DRIVE,
                        matcher.group(2), ClaimType.NONE, matcher.group(1).trim()));
            }
        }

        return items;
    }
}
