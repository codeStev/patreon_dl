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
