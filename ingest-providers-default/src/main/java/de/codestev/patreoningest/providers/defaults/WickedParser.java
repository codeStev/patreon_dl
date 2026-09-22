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
 * Wicked emails (Patreon's own DM-notification template, sent from
 * no-reply@community.patreon.com - matching happens on subject, not
 * from-address): noise-heavy marketing copy anchored on two structural
 * patterns, each a label line immediately followed by its URL on the next
 * line.
 *
 * <ul>
 *   <li>{@code N. Model Name:} then a gumroad.com link - a genuinely
 *       individual link (unlike a Drive folder, a Gumroad link is
 *       inherently single-product, so there's no ambiguity about it
 *       possibly containing more than one model). Used to claim ownership,
 *       not to download - {@code claimType = GUMROAD}.</li>
 *   <li>A {@code GOOGLE DRIVE ...} heading then a drive.google.com link -
 *       the same bulk-folder shape as Nomnom's links (per the design doc,
 *       this "term" folder supersedes the individual Gumroad links for
 *       actual file content), so {@code modelName = null}.</li>
 * </ul>
 * Everything else - the marketing prose, emoji section dividers, the
 * welcome-pack post link - is ignored.
 */
@Component
public class WickedParser implements CreatorMessageParser {

    private static final Pattern NUMBERED_ITEM = Pattern.compile("^\\d+\\.\\s+(.+):$");
    private static final Pattern TERM_FOLDER_HEADING = Pattern.compile("^GOOGLE DRIVE .*$");
    private static final Pattern GUMROAD_URL = Pattern.compile("^(https?://\\S*gumroad\\.com/\\S+)$");
    private static final Pattern DRIVE_URL = Pattern.compile("^(https?://drive\\.google\\.com/\\S+)$");

    @Override
    public String providerId() {
        return "wicked";
    }

    @Override
    public boolean supports(String fromAddress, String subject) {
        return subject != null && subject.toLowerCase(Locale.ROOT).contains("wicked");
    }

    @Override
    public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
        List<ParsedItem> items = new ArrayList<>();
        String pendingModelName = null;
        boolean pendingIsTermFolder = false;

        for (String rawLine : plainTextBody.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher numbered = NUMBERED_ITEM.matcher(line);
            if (numbered.matches()) {
                pendingModelName = numbered.group(1).trim();
                pendingIsTermFolder = false;
                continue;
            }

            if (TERM_FOLDER_HEADING.matcher(line).matches()) {
                pendingModelName = null;
                pendingIsTermFolder = true;
                continue;
            }

            // A label is only allowed to pair with the very next line - a
            // miss clears the pending state rather than letting it linger
            // and accidentally pair with something much further down.
            if (pendingModelName != null) {
                Matcher gumroad = GUMROAD_URL.matcher(line);
                if (gumroad.matches()) {
                    items.add(new ParsedItem(providerId(), null, null, SourceType.GUMROAD,
                            gumroad.group(1), ClaimType.GUMROAD, pendingModelName));
                }
                pendingModelName = null;
                continue;
            }

            if (pendingIsTermFolder) {
                Matcher drive = DRIVE_URL.matcher(line);
                if (drive.matches()) {
                    items.add(new ParsedItem(providerId(), null, null, SourceType.DRIVE,
                            drive.group(1), ClaimType.NONE, null));
                }
                pendingIsTermFolder = false;
            }
        }

        return items;
    }
}
