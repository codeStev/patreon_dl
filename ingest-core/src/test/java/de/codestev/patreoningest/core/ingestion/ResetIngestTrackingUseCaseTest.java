package de.codestev.patreoningest.core.ingestion;

import de.codestev.patreoningest.core.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Transactional
class ResetIngestTrackingUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ResetIngestTrackingUseCase resetIngestTrackingUseCase;

    @Autowired
    private ProcessedEmailRepository processedEmailRepository;

    @Test
    void clearsEveryProcessedEmailRow() {
        processedEmailRepository.save(new ProcessedEmail("inbox", 1L, "msg-1", "from@example.com",
                "subject", LocalDateTime.now(), "NomnomParser", ParseStatus.PARSED, null));
        processedEmailRepository.save(new ProcessedEmail("inbox", 2L, "msg-2", "from@example.com",
                "subject", LocalDateTime.now(), null, ParseStatus.NO_PARSER_MATCH, null));

        resetIngestTrackingUseCase.reset();

        assertThat(processedEmailRepository.count()).isZero();
    }

    @Test
    void isANoOpOnAnAlreadyEmptyTable() {
        resetIngestTrackingUseCase.reset();

        assertThat(processedEmailRepository.count()).isZero();
    }
}
