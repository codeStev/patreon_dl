package de.codestev.patreoningest.app;

import de.codestev.patreoningest.core.acquisition.ClaimStatus;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import de.codestev.patreoningest.core.ingestion.EmailMessage;
import de.codestev.patreoningest.core.ingestion.ParseStatus;
import de.codestev.patreoningest.core.ingestion.PollMailboxUseCase;
import de.codestev.patreoningest.core.ingestion.ProcessedEmailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the real wiring, not just each piece in isolation: a fake mailbox
// feeds a real email through the real NomnomParser (auto-registered via
// component scanning, same as production) into the real Acquisition
// persistence layer, backed by a real Postgres.
@Testcontainers
@SpringBootTest(classes = Application.class)
@Import(PollMailboxUseCaseEndToEndTest.TestMailboxConfig.class)
@Transactional
class PollMailboxUseCaseEndToEndTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final String NOMNOM_EMAIL_BODY = """
            [JULY MODELS]
            - Cool Dragon
            https://drive.google.com/drive/folders/e2e-test-folder
            """;

    @Autowired
    private PollMailboxUseCase pollMailboxUseCase;

    @Autowired
    private InMemoryMailboxPort mailboxPort;

    @Autowired
    private ProcessedEmailRepository processedEmailRepository;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @TestConfiguration
    static class TestMailboxConfig {
        @Bean
        InMemoryMailboxPort mailboxPort() {
            return new InMemoryMailboxPort();
        }
    }

    private static EmailMessage nomnomMessage(long uid) {
        return new EmailMessage("inbox", uid, "msg-" + uid, "updates@nomnom.example",
                "New models", NOMNOM_EMAIL_BODY, LocalDateTime.of(2026, 9, 1, 12, 0));
    }

    @Test
    void endToEndPollClaimsANewDriveSourceAndRecordsTheEmailAsParsed() {
        mailboxPort.setMessages(List.of(nomnomMessage(1L)));

        pollMailboxUseCase.poll();

        assertThat(downloadSourceRepository
                .findByCreatorAndSourceUrl("nomnom", "https://drive.google.com/drive/folders/e2e-test-folder"))
                .hasValueSatisfying(source -> assertThat(source.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED));

        Optional<de.codestev.patreoningest.core.ingestion.ProcessedEmail> processed =
                processedEmailRepository.findByMailboxAndUid("inbox", 1L);
        assertThat(processed).hasValueSatisfying(email -> {
            assertThat(email.getParseStatus()).isEqualTo(ParseStatus.PARSED);
            assertThat(email.getParserMatched()).isEqualTo("NomnomParser");
        });
    }

    @Test
    void pollingTheSameMailboxContentsTwiceDoesNotDoubleProcess() {
        mailboxPort.setMessages(List.of(nomnomMessage(2L)));

        pollMailboxUseCase.poll();
        pollMailboxUseCase.poll();

        assertThat(processedEmailRepository.count()).isEqualTo(1);
        assertThat(downloadSourceRepository.count()).isEqualTo(1);
    }
}
