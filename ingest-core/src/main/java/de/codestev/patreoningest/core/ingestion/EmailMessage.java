package de.codestev.patreoningest.core.ingestion;

import java.time.LocalDateTime;

public record EmailMessage(
        String mailbox,
        long uid,
        String messageId,
        String fromAddress,
        String subject,
        String body,
        LocalDateTime receivedAt
) {
}
