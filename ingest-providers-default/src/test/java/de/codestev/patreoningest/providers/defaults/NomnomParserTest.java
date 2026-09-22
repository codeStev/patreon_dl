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
    void supportsMatchesOnFromAddressContainingNomnom() {
        assertThat(parser.supports("updates@nomnom.example", "New models")).isTrue();
        assertThat(parser.supports("someone-else@example.com", "New models")).isFalse();
    }

    @Test
    void parsesTwoSectionsIntoTwoWholeFolderItems() {
        List<ParsedItem> items = parser.parse(fixture("two_sections.txt"), LocalDate.of(2026, 9, 1));

        assertThat(items).containsExactly(
                new ParsedItem("nomnom", "regular", "JULY", SourceType.DRIVE,
                        "https://drive.google.com/drive/folders/abc123", ClaimType.NONE, null),
                new ParsedItem("nomnom", "extra", "AUGUST", SourceType.DRIVE,
                        "https://drive.google.com/drive/folders/xyz789", ClaimType.NONE, null));
    }

    @Test
    void parsesSingleSection() {
        List<ParsedItem> items = parser.parse(fixture("single_section.txt"), LocalDate.of(2026, 9, 1));

        assertThat(items).containsExactly(
                new ParsedItem("nomnom", "regular", "JULY", SourceType.DRIVE,
                        "https://drive.google.com/drive/folders/onlyone", ClaimType.NONE, null));
    }

    @Test
    void aUrlWithNoPrecedingHeaderAndBulletsProducesNoItem() {
        List<ParsedItem> items = parser.parse(
                "https://drive.google.com/drive/folders/orphan", LocalDate.of(2026, 9, 1));

        assertThat(items).isEmpty();
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
