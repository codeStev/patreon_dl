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

class WickedParserTest {

    private final WickedParser parser = new WickedParser();

    @Test
    void supportsMatchesOnSubjectContainingWicked() {
        assertThat(parser.supports("no-reply@community.patreon.com",
                "Wicked hat dir eine Nachricht gesendet")).isTrue();
        assertThat(parser.supports("no-reply@community.patreon.com",
                "Nomnom Figures hat dir eine Nachricht gesendet")).isFalse();
    }

    @Test
    void parsesAllNineteenNumberedGumroadItemsAndAllThreeTermFolders() {
        List<ParsedItem> items = parser.parse(fixture("july_term_2026.txt"), LocalDate.of(2026, 9, 1));

        List<ParsedItem> gumroadItems = items.stream().filter(i -> i.sourceType() == SourceType.GUMROAD).toList();
        List<ParsedItem> driveItems = items.stream().filter(i -> i.sourceType() == SourceType.DRIVE).toList();

        assertThat(gumroadItems).hasSize(19);
        assertThat(driveItems).hasSize(3);
        assertThat(items).hasSize(22);
    }

    @Test
    void numberedGumroadItemsAreNamedAndUseClaimTypeGumroad() {
        List<ParsedItem> items = parser.parse(fixture("july_term_2026.txt"), LocalDate.of(2026, 9, 1));

        assertThat(items).contains(new ParsedItem("wicked", null, null, SourceType.GUMROAD,
                "https://3dwicked.gumroad.com/l/ClintBartonRoninS/sevk9xc",
                ClaimType.GUMROAD, "Clint Barton Ronin Sculpture Scale 1/6 (440mm)"));
        assertThat(items).contains(new ParsedItem("wicked", null, null, SourceType.GUMROAD,
                "https://3dwicked.gumroad.com/l/TyraelPB/to60s2z",
                ClaimType.GUMROAD, "Tyrael Portrait Bust Scale 1/4 (396mm)"));
    }

    @Test
    void termFoldersAreWholeFolderDriveItemsWithNoClaimNeeded() {
        List<ParsedItem> items = parser.parse(fixture("july_term_2026.txt"), LocalDate.of(2026, 9, 1));

        assertThat(items).contains(
                new ParsedItem("wicked", null, null, SourceType.DRIVE,
                        "https://drive.google.com/drive/folders/1s2QAA8E-i3Vt3kG0OHHOAKmP1Golj-v9?usp=sharing",
                        ClaimType.NONE, null),
                new ParsedItem("wicked", null, null, SourceType.DRIVE,
                        "https://drive.google.com/drive/folders/1BzZ4HBfy7j5KyVqp4Dohg0pwIKii81d0?usp=sharing",
                        ClaimType.NONE, null),
                new ParsedItem("wicked", null, null, SourceType.DRIVE,
                        "https://drive.google.com/drive/folders/1Lod87w_uYApju60KUgS_0fl0v_cx0bxs?usp=sharing",
                        ClaimType.NONE, null));
    }

    @Test
    void theWelcomePackAndOtherPatreonLinksProduceNoItems() {
        List<ParsedItem> items = parser.parse(fixture("july_term_2026.txt"), LocalDate.of(2026, 9, 1));

        assertThat(items).noneMatch(item -> item.sourceUrl().contains("patreon.com/posts"));
    }

    private static String fixture(String name) {
        try (InputStream in = WickedParserTest.class.getResourceAsStream("/emails/wicked/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
