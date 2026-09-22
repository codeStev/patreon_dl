package de.codestev.patreoningest.core.ingestion;

import de.codestev.patreoningest.core.acquisition.RegisterParsedItemsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PollMailboxUseCase {

    private static final Logger log = LoggerFactory.getLogger(PollMailboxUseCase.class);

    private final MailboxPort mailboxPort;
    private final ParserRegistry parserRegistry;
    private final RegisterParsedItemsUseCase registerParsedItemsUseCase;
    private final ProcessedEmailRepository processedEmailRepository;

    public PollMailboxUseCase(MailboxPort mailboxPort,
                               ParserRegistry parserRegistry,
                               RegisterParsedItemsUseCase registerParsedItemsUseCase,
                               ProcessedEmailRepository processedEmailRepository) {
        this.mailboxPort = mailboxPort;
        this.parserRegistry = parserRegistry;
        this.registerParsedItemsUseCase = registerParsedItemsUseCase;
        this.processedEmailRepository = processedEmailRepository;
    }

    public void poll() {
        log.info("Mailbox poll starting (adapter={})", mailboxPort.getClass().getSimpleName());

        List<EmailMessage> messages;
        try {
            messages = mailboxPort.fetchNewMessages();
        } catch (RuntimeException e) {
            log.error("Mailbox poll failed while fetching messages", e);
            throw e;
        }
        log.info("Mailbox poll fetched {} message(s)", messages.size());

        int newCount = 0;
        int skippedCount = 0;
        for (EmailMessage message : messages) {
            // Restart-safe idempotency lives here, not in the adapter - a
            // message re-fetched after a restart (or a flag reset by
            // another mail client) must never be reprocessed.
            if (processedEmailRepository.findByMailboxAndUid(message.mailbox(), message.uid()).isPresent()) {
                skippedCount++;
                continue;
            }

            ParseResult result = parserRegistry.parse(message);
            if (result.status() == ParseStatus.PARSED) {
                registerParsedItemsUseCase.register(result.items());
            }
            log.info("Processed email uid={} from={} subject={} -> {}{}",
                    message.uid(), message.fromAddress(), message.subject(), result.status(),
                    result.errorMessage() != null ? " (" + result.errorMessage() + ")" : "");

            processedEmailRepository.save(new ProcessedEmail(
                    message.mailbox(), message.uid(), message.messageId(), message.fromAddress(),
                    message.subject(), message.receivedAt(), result.parserMatched(),
                    result.status(), result.errorMessage()));
            newCount++;
        }

        log.info("Mailbox poll complete: {} new, {} already processed", newCount, skippedCount);
    }
}
