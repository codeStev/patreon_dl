package de.codestev.patreoningest.app;

import de.codestev.patreoningest.core.ingestion.EmailMessage;
import de.codestev.patreoningest.core.ingestion.ProcessedEmailRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the scheduler wiring itself works live, not just that its pieces
// (DynamicPollTrigger, PollMailboxUseCase) are individually correct - the
// background scheduled thread must actually fire and commit real work.
// Deliberately NOT @Transactional: the scheduler runs on its own thread, so
// the test needs to observe real commits, not the test thread's own
// uncommitted transaction.
@Testcontainers
@SpringBootTest(classes = Application.class, properties = "patreon.ingestion.polling.enabled=true")
@Import(MailboxPollingSchedulerTest.TestMailboxConfig.class)
class MailboxPollingSchedulerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final String NOMNOM_EMAIL_BODY = """
            [JULY MODELS]
            - Cool Dragon
            https://drive.google.com/drive/folders/scheduler-test
            """;

    @Autowired
    private ProcessedEmailRepository processedEmailRepository;

    @TestConfiguration
    static class TestMailboxConfig {
        // Pre-populated at bean-creation time, not after context startup -
        // the scheduler's first tick fires immediately (DynamicPollTrigger
        // runs right away when there's no prior execution), so the message
        // must already be there before that first tick can race for it.
        @Bean
        InMemoryMailboxPort mailboxPort() {
            InMemoryMailboxPort port = new InMemoryMailboxPort();
            port.setMessages(List.of(new EmailMessage("inbox", 99L, "msg-99",
                    "updates@nomnom.example", "New models", NOMNOM_EMAIL_BODY, LocalDateTime.now())));
            return port;
        }
    }

    @Test
    void theSchedulerActuallyFiresAndProcessesTheMailboxOnStartup() {
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(processedEmailRepository.count()).isEqualTo(1));
    }
}
