package de.codestev.patreoningest.providers.defaults;

import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.SourceType;
import de.codestev.patreoningest.core.ingestion.ParsedItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NomnomParserTest {

    private final NomnomParser parser = new NomnomParser();

    @Test
    void supportsMatchesOnSubjectContainingNomnom() {
        // Real Patreon DM notifications all come from the same Patreon
        // system address regardless of creator - matching has to be on
        // subject ("Nomnom Figures hat dir eine Nachricht gesendet"), not
        // from-address.
        assertThat(parser.supports("no-reply@community.patreon.com",
                "Nomnom Figures hat dir eine Nachricht gesendet")).isTrue();
        assertThat(parser.supports("no-reply@community.patreon.com",
                "Wicked hat dir eine Nachricht gesendet")).isFalse();
    }

    @Test
    void everyDriveLinkBecomesAWholeFolderItemRegardlessOfBulletCount() {
        List<ParsedItem> items = parser.parse(fixture("two_sections.txt"), LocalDate.of(2026, 9, 1));

        assertThat(items).containsExactly(
                new ParsedItem("nomnom", null, null, SourceType.DRIVE,
                        "https://drive.google.com/drive/folders/abc123", ClaimType.NONE, null),
                new ParsedItem("nomnom", null, null, SourceType.DRIVE,
                        "https://drive.google.com/drive/folders/xyz789", ClaimType.NONE, null));
    }

    @Test
    void aSingleBulletDoesNotMakeItAnIndividualItem() {
        // Even with exactly one model currently listed, it's still a
        // folder link, not a dedicated per-model link - Nomnom never hands
        // out the latter.
        List<ParsedItem> items = parser.parse(fixture("single_section.txt"), LocalDate.of(2026, 9, 1));

        assertThat(items).containsExactly(new ParsedItem("nomnom", null, null, SourceType.DRIVE,
                "https://drive.google.com/drive/folders/onlyone", ClaimType.NONE, null));
    }

    @Test
    void aNonDriveUrlIsIgnored() {
        // "Legacy Models:" pointing at a Patreon collection page, and the
        // inline FAQ link - neither is a Drive folder, neither should
        // produce an item.
        List<ParsedItem> items = parser.parse(fixture("real_august_2026.txt"), LocalDate.of(2026, 8, 2));

        assertThat(items).noneMatch(item -> item.sourceUrl().contains("patreon.com"));
    }

    @Test
    void parsesTheRealAugust2026Email() {
        List<ParsedItem> items = parser.parse(fixture("real_august_2026.txt"), LocalDate.of(2026, 8, 2));

        // Six drive.google.com links in the body, each its own whole-folder
        // item - "AUGUST Extra Models" having two links, one per bullet,
        // doesn't change anything: they're still just folders.
        assertThat(items).extracting(ParsedItem::sourceUrl).containsExactly(
                "https://drive.google.com/drive/folders/17-F4ktEzLVM0-gKZwRGDeVQwnAyWZW-1?usp=sharing",
                "https://drive.google.com/drive/folders/1WKpn_ogGAG7fBdmhSDkQPus8qECgo56s?usp=sharing",
                "https://drive.google.com/drive/folders/194Gl26fQKzpkxdgW0AKrompCeDb_mc-Y?usp=sharing",
                "https://drive.google.com/drive/folders/1YRKCm16u4BSZ45ZEbHR0UrTM9JeMa2YX?usp=sharing",
                "https://drive.google.com/drive/folders/19SBRBCX6Yia5ZWAiSqFHoBL_pueNwEGQ?usp=sharing",
                "https://drive.google.com/drive/folders/19yV4KxDYOI8-0doiDHIQUl6wtfPZ8fXq?usp=sharing");
        assertThat(items).allMatch(item -> item.modelName() == null);
    }

    private static String fixture(String name) {
        try (InputStream in = NomnomParserTest.class.getResourceAsStream("/emails/nomnom/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
