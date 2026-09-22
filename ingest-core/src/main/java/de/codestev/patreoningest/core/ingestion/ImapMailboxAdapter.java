package de.codestev.patreoningest.core.ingestion;

import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// Only active when patreon.imap.host is actually configured - inert in
// tests and any deployment that hasn't set up a mailbox yet, so it never
// collides with a test's own MailboxPort fake.
@Component
@ConditionalOnProperty(prefix = "patreon.imap", name = "host")
public class ImapMailboxAdapter implements MailboxPort {

    private final ImapProperties properties;

    public ImapMailboxAdapter(ImapProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<EmailMessage> fetchNewMessages() {
        String protocol = properties.useSsl() ? "imaps" : "imap";
        Properties sessionProperties = new Properties();
        sessionProperties.put("mail.store.protocol", protocol);

        List<EmailMessage> messages = new ArrayList<>();
        Session session = Session.getInstance(sessionProperties);
        try (Store store = session.getStore(protocol)) {
            store.connect(properties.host(), properties.port(), properties.username(), properties.password());
            Folder folder = store.getFolder(properties.folder());
            folder.open(Folder.READ_ONLY);
            try {
                for (Message message : folder.getMessages()) {
                    messages.add(toEmailMessage(folder, message));
                }
            } finally {
                folder.close(false);
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to poll IMAP mailbox " + properties.folder(), e);
        }
        return messages;
    }

    private EmailMessage toEmailMessage(Folder folder, Message message) throws MessagingException {
        long uid = (folder instanceof UIDFolder uidFolder)
                ? uidFolder.getUID(message)
                : message.getMessageNumber();

        String fromAddress = "";
        if (message.getFrom() != null && message.getFrom().length > 0
                && message.getFrom()[0] instanceof InternetAddress address) {
            fromAddress = address.getAddress();
        }

        String[] messageIdHeader = message.getHeader("Message-ID");
        String messageId = (messageIdHeader != null && messageIdHeader.length > 0) ? messageIdHeader[0] : null;

        LocalDateTime receivedAt = message.getReceivedDate() != null
                ? message.getReceivedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.now();

        return new EmailMessage(properties.folder(), uid, messageId, fromAddress,
                message.getSubject(), extractPlainText(message), receivedAt);
    }

    private static String extractPlainText(Message message) throws MessagingException {
        try {
            Object content = message.getContent();
            if (content instanceof String text) {
                return text;
            }
            if (content instanceof Multipart multipart) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart part = multipart.getBodyPart(i);
                    if (part.isMimeType("text/plain")) {
                        builder.append(part.getContent());
                    }
                }
                return builder.toString();
            }
            return "";
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessagingException("Failed to read message content", e);
        }
    }
}
