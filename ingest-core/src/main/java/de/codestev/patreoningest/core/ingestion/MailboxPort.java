package de.codestev.patreoningest.core.ingestion;

import java.util.List;

public interface MailboxPort {

    List<EmailMessage> fetchNewMessages();
}
