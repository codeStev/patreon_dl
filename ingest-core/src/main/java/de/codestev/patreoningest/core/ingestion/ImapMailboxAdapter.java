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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

// Only active when patreon.imap.host is actually configured - inert in
// tests and any deployment that hasn't set up a mailbox yet, so it never
// collides with a test's own MailboxPort fake. Deliberately NOT
// @ConditionalOnProperty: that annotation treats a present-but-empty string
// as satisfied (only an explicit "false" or a fully absent key fails it),
// which is exactly what docker-compose's `${VAR:-}` substitution produces
// when the .env value is unset - the key always exists, just empty. The
// custom condition below checks for actual non-blank content instead.
@Component
@Conditional(ImapMailboxAdapter.ImapHostConfigured.class)
public class ImapMailboxAdapter implements MailboxPort {

    private static final Logger log = LoggerFactory.getLogger(ImapMailboxAdapter.class);

    private final ImapProperties properties;
    private final ProcessedEmailRepository processedEmailRepository;

    public ImapMailboxAdapter(ImapProperties properties, ProcessedEmailRepository processedEmailRepository) {
        this.properties = properties;
        this.processedEmailRepository = processedEmailRepository;
    }

    @Override
    public List<EmailMessage> fetchNewMessages() {
        String protocol = properties.useSsl() ? "imaps" : "imap";
        Properties sessionProperties = new Properties();
        sessionProperties.put("mail.store.protocol", protocol);

        log.info("Connecting to IMAP {}:{} as {} (folder={}, ssl={})",
                properties.host(), properties.port(), properties.username(), properties.folder(),
                properties.useSsl());

        List<EmailMessage> messages = new ArrayList<>();
        Session session = Session.getInstance(sessionProperties);
        try (Store store = session.getStore(protocol)) {
            store.connect(properties.host(), properties.port(), properties.username(), properties.password());
            Folder folder = store.getFolder(properties.folder());
            folder.open(Folder.READ_ONLY);
            log.info("IMAP connected - folder '{}' has {} message(s) total", properties.folder(), folder.getMessageCount());
            try {
                for (Message message : messagesToFetch(folder)) {
                    messages.add(toEmailMessage(folder, message));
                }
            } finally {
                folder.close(false);
            }
        } catch (MessagingException e) {
            log.error("IMAP connection to {}:{} failed", properties.host(), properties.port(), e);
            throw new IllegalStateException("Failed to poll IMAP mailbox " + properties.folder(), e);
        }
        return messages;
    }

    // Only ever fetch messages newer than the highest UID already recorded
    // for this mailbox - never re-scan the whole folder on every poll. The
    // very first poll ever for a mailbox has no prior record, so it starts
    // from UID 1 and does scan everything currently there once (a personal
    // inbox's existing Patreon history is exactly what should be picked up)
    // - the fix is that every poll AFTER that is incremental, not that the
    // first one is free. A years-old inbox pointed at this adapter means one
    // slow initial pass, never a repeated one.
    private Message[] messagesToFetch(Folder folder) throws MessagingException {
        if (!(folder instanceof UIDFolder uidFolder)) {
            log.warn("IMAP server does not support UIDs - falling back to fetching the whole folder every poll");
            return folder.getMessages();
        }

        long startUid = processedEmailRepository.findMaxUidByMailbox(properties.folder())
                .map(uid -> uid + 1)
                .orElse(1L);

        Message[] found = uidFolder.getMessagesByUID(startUid, UIDFolder.LASTUID);
        log.info("Fetching messages with UID >= {} ({} found)", startUid, found.length);
        return found;
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

    static class ImapHostConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("patreon.imap.host"));
        }
    }
}
