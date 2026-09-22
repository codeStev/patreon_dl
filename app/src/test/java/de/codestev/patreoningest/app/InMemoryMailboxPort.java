package de.codestev.patreoningest.app;

import de.codestev.patreoningest.core.ingestion.EmailMessage;
import de.codestev.patreoningest.core.ingestion.MailboxPort;

import java.util.ArrayList;
import java.util.List;

// Test-only fake: fetchNewMessages() keeps returning whatever was last set,
// simulating an IMAP server that hasn't marked/removed anything - this is
// exactly what exercises PollMailboxUseCase's own idempotency guard rather
// than relying on the mailbox to not re-serve a message.
class InMemoryMailboxPort implements MailboxPort {

    private List<EmailMessage> messages = new ArrayList<>();

    void setMessages(List<EmailMessage> messages) {
        this.messages = messages;
    }

    @Override
    public List<EmailMessage> fetchNewMessages() {
        return messages;
    }
}
