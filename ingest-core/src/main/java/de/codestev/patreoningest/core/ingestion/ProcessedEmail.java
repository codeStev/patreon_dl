package de.codestev.patreoningest.core.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_email")
public class ProcessedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String mailbox;

    @Column(nullable = false)
    private long uid;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "from_address")
    private String fromAddress;

    private String subject;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "parser_matched")
    private String parserMatched;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false)
    private ParseStatus parseStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedEmail() {
        // JPA
    }

    public ProcessedEmail(String mailbox, long uid, String messageId, String fromAddress,
                           String subject, LocalDateTime receivedAt, String parserMatched,
                           ParseStatus parseStatus, String errorMessage) {
        this.mailbox = mailbox;
        this.uid = uid;
        this.messageId = messageId;
        this.fromAddress = fromAddress;
        this.subject = subject;
        this.receivedAt = receivedAt;
        this.parserMatched = parserMatched;
        this.parseStatus = parseStatus;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getMailbox() {
        return mailbox;
    }

    public long getUid() {
        return uid;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getParserMatched() {
        return parserMatched;
    }

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
