package de.codestev.patreoningest.core.ingestion;

import java.util.List;

public record ParseResult(
        ParseStatus status,
        String parserMatched,
        List<ParsedItem> items,
        String errorMessage
) {

    public static ParseResult parsed(String parserMatched, List<ParsedItem> items) {
        return new ParseResult(ParseStatus.PARSED, parserMatched, items, null);
    }

    public static ParseResult noMatch() {
        return new ParseResult(ParseStatus.NO_PARSER_MATCH, null, List.of(), null);
    }

    public static ParseResult error(String parserMatched, String errorMessage) {
        return new ParseResult(ParseStatus.PARSE_ERROR, parserMatched, List.of(), errorMessage);
    }
}
