package de.codestev.patreoningest.core.ingestion;

import de.codestev.patreoningest.core.acquisition.RegisterParsedItemsUseCase;
import org.springframework.stereotype.Component;

@Component
public class PollMailboxUseCase {

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
        for (EmailMessage message : mailboxPort.fetchNewMessages()) {
            // Restart-safe idempotency lives here, not in the adapter - a
            // message re-fetched after a restart (or a flag reset by
            // another mail client) must never be reprocessed.
            if (processedEmailRepository.findByMailboxAndUid(message.mailbox(), message.uid()).isPresent()) {
                continue;
            }

            ParseResult result = parserRegistry.parse(message);
            if (result.status() == ParseStatus.PARSED) {
                registerParsedItemsUseCase.register(result.items());
            }

            processedEmailRepository.save(new ProcessedEmail(
                    message.mailbox(), message.uid(), message.messageId(), message.fromAddress(),
                    message.subject(), message.receivedAt(), result.parserMatched(),
                    result.status(), result.errorMessage()));
        }
    }
}
