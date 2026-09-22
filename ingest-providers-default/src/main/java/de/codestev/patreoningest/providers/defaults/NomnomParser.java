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
 * Nomnom emails (Patreon's own DM-notification template, sent from
 * no-reply@community.patreon.com - matching happens on subject, not
 * from-address): every Google Drive link in the body is a folder,
 * regardless of how many models the surrounding bullet list currently
 * names - Nomnom never hands out a dedicated per-model link, only shared
 * folders that can gain more files later. So the bullet list is pure
 * decoration for the human reader and is never parsed at all: the folder
 * is the source of truth for what's inside (see the design doc), not this
 * text - every {@code drive.google.com} link on its own line becomes one
 * whole-folder {@code ParsedItem} ({@code modelName = null}).
 */
@Component
public class NomnomParser implements CreatorMessageParser {

    private static final Pattern DRIVE_URL = Pattern.compile("^(https?://drive\\.google\\.com/\\S+)$");

    @Override
    public String providerId() {
        return "nomnom";
    }

    @Override
    public boolean supports(String fromAddress, String subject) {
        return subject != null && subject.toLowerCase(Locale.ROOT).contains("nomnom");
    }

    @Override
    public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
        List<ParsedItem> items = new ArrayList<>();

        for (String rawLine : plainTextBody.split("\\R")) {
            String line = rawLine.trim();
            Matcher urlMatcher = DRIVE_URL.matcher(line);
            if (urlMatcher.matches()) {
                items.add(new ParsedItem(providerId(), null, null,
                        SourceType.DRIVE, urlMatcher.group(1), ClaimType.NONE, null));
            }
        }

        return items;
    }
}
