package de.codestev.patreoningest.core.ingestion;

import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.SourceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParserRegistryTest {

    private static final EmailMessage MESSAGE = new EmailMessage(
            "inbox", 1L, "msg-1", "creator@example.com", "New models",
            "body", LocalDateTime.of(2026, 9, 1, 12, 0));

    @Test
    void returnsNoMatchWhenNoParserSupportsTheMessage() {
        ParserRegistry registry = new ParserRegistry(List.of(new NeverMatchesParser()));

        ParseResult result = registry.parse(MESSAGE);

        assertThat(result.status()).isEqualTo(ParseStatus.NO_PARSER_MATCH);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void returnsParsedItemsFromTheSingleMatchingParser() {
        ParsedItem item = new ParsedItem("nomnom", "regular", "2026-09",
                SourceType.DRIVE, "https://drive.example/abc", ClaimType.NONE, null);
        ParserRegistry registry = new ParserRegistry(List.of(
                new NeverMatchesParser(), new AlwaysMatchesParser(List.of(item))));

        ParseResult result = registry.parse(MESSAGE);

        assertThat(result.status()).isEqualTo(ParseStatus.PARSED);
        assertThat(result.parserMatched()).isEqualTo("AlwaysMatchesParser");
        assertThat(result.items()).containsExactly(item);
    }

    @Test
    void aThrowingParserIsReportedAsParseErrorAndDoesNotPropagate() {
        ParserRegistry registry = new ParserRegistry(List.of(new ThrowingParser()));

        ParseResult result = registry.parse(MESSAGE);

        assertThat(result.status()).isEqualTo(ParseStatus.PARSE_ERROR);
        assertThat(result.parserMatched()).isEqualTo("ThrowingParser");
        assertThat(result.errorMessage()).isEqualTo("boom");
    }

    private static class NeverMatchesParser implements CreatorMessageParser {
        @Override
        public String providerId() {
            return "never-matches";
        }

        @Override
        public boolean supports(String fromAddress, String subject) {
            return false;
        }

        @Override
        public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
            throw new AssertionError("should never be called");
        }
    }

    private static class AlwaysMatchesParser implements CreatorMessageParser {
        private final List<ParsedItem> items;

        AlwaysMatchesParser(List<ParsedItem> items) {
            this.items = items;
        }

        @Override
        public String providerId() {
            return "always-matches";
        }

        @Override
        public boolean supports(String fromAddress, String subject) {
            return true;
        }

        @Override
        public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
            return items;
        }
    }

    private static class ThrowingParser implements CreatorMessageParser {
        @Override
        public String providerId() {
            return "throwing";
        }

        @Override
        public boolean supports(String fromAddress, String subject) {
            return true;
        }

        @Override
        public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
            throw new IllegalStateException("boom");
        }
    }
}
