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
 * Nomnom emails: bracketed or plain-colon section headers
 * ({@code [JULY MODELS]}, {@code AUGUST Extra Models:}), each owning a run
 * of bullet lines ({@code -} or {@code ▫️}) followed by one trailing Google
 * Drive URL that applies to the whole section.
 *
 * <p>Per the design doc, the folder is the source of truth for what files
 * exist, not this bullet list - so bullets are only used to find where a
 * section ends (its trailing URL), never turned into named
 * {@link ParsedItem}s. Each section becomes exactly one whole-folder
 * {@code ParsedItem} ({@code modelName = null}).
 */
@Component
public class NomnomParser implements CreatorMessageParser {

    private static final Pattern BRACKETED_HEADER = Pattern.compile("^\\[(.+)]$");
    private static final Pattern COLON_HEADER = Pattern.compile("^(.+):$");
    private static final Pattern BULLET = Pattern.compile("^[-▫️]\\s*.+$");
    private static final Pattern DRIVE_URL = Pattern.compile("^(https?://\\S+)$");

    @Override
    public String providerId() {
        return "nomnom";
    }

    @Override
    public boolean supports(String fromAddress, String subject) {
        return fromAddress != null && fromAddress.toLowerCase(Locale.ROOT).contains("nomnom");
    }

    @Override
    public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
        List<ParsedItem> items = new ArrayList<>();
        String currentMonth = null;
        String currentCategory = null;
        boolean sawBulletInCurrentSection = false;

        for (String rawLine : plainTextBody.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String header = matchHeader(line);
            if (header != null) {
                String[] parts = header.trim().split("\\s+", 2);
                currentMonth = parts[0].toUpperCase(Locale.ROOT);
                currentCategory = categoryFrom(parts.length > 1 ? parts[1] : "");
                sawBulletInCurrentSection = false;
                continue;
            }

            if (BULLET.matcher(line).matches()) {
                sawBulletInCurrentSection = true;
                continue;
            }

            Matcher urlMatcher = DRIVE_URL.matcher(line);
            if (urlMatcher.matches() && currentMonth != null && sawBulletInCurrentSection) {
                items.add(new ParsedItem(providerId(), currentCategory, currentMonth,
                        SourceType.DRIVE, urlMatcher.group(1), ClaimType.NONE, null));
                currentMonth = null;
                currentCategory = null;
                sawBulletInCurrentSection = false;
            }
        }

        return items;
    }

    private static String matchHeader(String line) {
        Matcher bracketed = BRACKETED_HEADER.matcher(line);
        if (bracketed.matches()) {
            return bracketed.group(1);
        }
        Matcher colon = COLON_HEADER.matcher(line);
        if (colon.matches() && !BULLET.matcher(line).matches()) {
            return colon.group(1);
        }
        return null;
    }

    private static String categoryFrom(String headerRemainder) {
        String normalized = headerRemainder.toLowerCase(Locale.ROOT);
        if (normalized.contains("extra")) {
            return "extra";
        }
        if (normalized.contains("loyalty")) {
            return "loyalty";
        }
        if (normalized.contains("lootbox")) {
            return "lootbox";
        }
        if (normalized.contains("keycap")) {
            return "keycap";
        }
        if (normalized.contains("term")) {
            return "term";
        }
        return "regular";
    }
}
