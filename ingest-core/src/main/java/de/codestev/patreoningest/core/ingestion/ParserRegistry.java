package de.codestev.patreoningest.core.ingestion;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParserRegistry {

    private final List<CreatorMessageParser> parsers;

    public ParserRegistry(List<CreatorMessageParser> parsers) {
        this.parsers = parsers;
    }

    // A malformed or unrecognized email must never stop the poll loop - a
    // parser throwing is caught here and reported as PARSE_ERROR, never
    // propagated to the caller.
    public ParseResult parse(EmailMessage message) {
        for (CreatorMessageParser parser : parsers) {
            if (parser.supports(message.fromAddress(), message.subject())) {
                String parserName = parser.getClass().getSimpleName();
                try {
                    List<ParsedItem> items = parser.parse(message.body(), message.receivedAt().toLocalDate());
                    return ParseResult.parsed(parserName, items);
                } catch (Exception e) {
                    return ParseResult.error(parserName, e.getMessage());
                }
            }
        }
        return ParseResult.noMatch();
    }
}
