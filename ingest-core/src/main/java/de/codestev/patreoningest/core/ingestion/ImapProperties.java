package de.codestev.patreoningest.core.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "patreon.imap")
public record ImapProperties(
        String host,
        @DefaultValue("993") int port,
        String username,
        String password,
        @DefaultValue("INBOX") String folder,
        @DefaultValue("true") boolean useSsl
) {
}
