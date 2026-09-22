package de.codestev.patreoningest.providers.defaults;

import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.SourceType;
import de.codestev.patreoningest.core.ingestion.CreatorMessageParser;
import de.codestev.patreoningest.core.ingestion.ParsedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BulkamancerParser.class);

    private static final Pattern NAMED_DRIVE_LINK =
            Pattern.compile("^(.+?):\\s*(https?://drive\\.google\\.com/\\S+)$");

    // A real hand-typed model name is short and plain text. Guards against
    // a real incident: a malformed email left one "line" (post-split(\\R))
    // as several hundred characters of raw HTML template markup followed
    // by ": <drive-url>" - NAMED_DRIVE_LINK's ".matches()" is anchored end
    // to end, so the non-greedy "(.+?):" still backtracked across the
    // entire HTML blob to find a colon that let the rest of the pattern
    // match, producing a "model name" that was actually HTML soup. That
    // name then blew past Linux's ~255-byte filename limit when used as a
    // download directory name, crashing every download attempt.
    private static final int MAX_PLAUSIBLE_NAME_LENGTH = 100;

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
            if (!matcher.matches()) {
                continue;
            }
            String modelName = matcher.group(1).trim();
            if (!isPlausibleModelName(modelName)) {
                log.warn("Rejected implausible Bulkamancer model name (length {}, starts with: {}) - "
                                + "likely HTML leaking into the plain-text body rather than a real name",
                        modelName.length(), modelName.substring(0, Math.min(40, modelName.length())));
                continue;
            }
            items.add(new ParsedItem(providerId(), null, null, SourceType.DRIVE,
                    matcher.group(2), ClaimType.NONE, modelName));
        }

        return items;
    }

    private static boolean isPlausibleModelName(String name) {
        return !name.isEmpty()
                && name.length() <= MAX_PLAUSIBLE_NAME_LENGTH
                && !name.contains("<")
                && !name.contains(">");
    }
}
