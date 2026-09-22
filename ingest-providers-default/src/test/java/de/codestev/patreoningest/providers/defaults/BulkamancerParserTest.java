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

class BulkamancerParserTest {

    private final BulkamancerParser parser = new BulkamancerParser();

    @Test
    void supportsMatchesOnSubjectContainingBulkamancer() {
        assertThat(parser.supports("no-reply@community.patreon.com",
                "Bulkamancer Sculpts hat dir eine Nachricht gesendet")).isTrue();
        assertThat(parser.supports("no-reply@community.patreon.com",
                "Wicked hat dir eine Nachricht gesendet")).isFalse();
    }

    @Test
    void parsesAStandaloneNamedDriveLink() {
        List<ParsedItem> items = parser.parse(fixture("wolverine_standalone.txt"), LocalDate.of(2026, 9, 21));

        assertThat(items).containsExactly(new ParsedItem("bulkamancer", null, null, SourceType.DRIVE,
                "https://drive.google.com/drive/folders/1Q199aX_UUsNpXr4u9qdea5evnA3gNBS4?patron=38db",
                ClaimType.NONE, "Wolverine"));
    }

    @Test
    void aLineWithoutAColonBeforeTheUrlProducesNoItem() {
        List<ParsedItem> items = parser.parse(
                "https://drive.google.com/drive/folders/orphan", LocalDate.of(2026, 9, 21));

        assertThat(items).isEmpty();
    }

    // Reproduces a real incident: a malformed email left raw HTML template
    // markup concatenated onto one "line" with no real line break before
    // "Richter: <url>". The non-greedy "(.+?):" in NAMED_DRIVE_LINK still
    // backtracks across the whole blob to make the anchored match succeed,
    // so without this guard the "model name" would be several hundred
    // characters of HTML - which then crashed download-directory creation
    // (ENAMETOOLONG) on every retry.
    @Test
    void aLineWhereHtmlLeakedIntoTheNamePortionProducesNoItem() {
        String htmlSoupLine = "<tbody><tr><td align=\"left\" class=\"preserve-white-space\" "
                + "style=\"font-size:0px;padding:10px 16px;\"><div style=\"color:#000000;\">"
                + "<p style=\"margin-top:0;\">Richter: https://drive.google.com/drive/folders/richter";

        List<ParsedItem> items = parser.parse(htmlSoupLine, LocalDate.of(2026, 9, 21));

        assertThat(items).isEmpty();
    }

    @Test
    void anImplausiblyLongNameProducesNoItem() {
        String longName = "x".repeat(150);

        List<ParsedItem> items = parser.parse(
                longName + ": https://drive.google.com/drive/folders/long", LocalDate.of(2026, 9, 21));

        assertThat(items).isEmpty();
    }

    private static String fixture(String name) {
        try (InputStream in = BulkamancerParserTest.class.getResourceAsStream("/emails/bulkamancer/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
