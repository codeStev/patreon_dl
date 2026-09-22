package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.app.Application;
import de.codestev.patreoningest.core.ingestion.ParseStatus;
import de.codestev.patreoningest.core.ingestion.ProcessedEmail;
import de.codestev.patreoningest.core.ingestion.ProcessedEmailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class IngestionAdminControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProcessedEmailRepository processedEmailRepository;

    @Test
    void resetTrackingClearsProcessedEmailAndReturns202() throws Exception {
        processedEmailRepository.save(new ProcessedEmail("inbox", 1L, "msg-1", "from@example.com",
                "subject", LocalDateTime.now(), "NomnomParser", ParseStatus.PARSED, null));

        mockMvc.perform(post("/api/ingestion/reset-tracking"))
                .andExpect(status().isAccepted());

        assertThat(processedEmailRepository.count()).isZero();
    }
}
