package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.app.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Transactional isolates each test method (MOCK web environment runs
// requests on the test's own thread, so the transaction genuinely wraps
// both the PUT and the follow-up GET) - without it, tests would leak
// committed state into each other in undefined order.
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class IngestionSettingsControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getReturnsTheDefaultIntervalWhenNothingWasEverSet() throws Exception {
        mockMvc.perform(get("/api/ingestion-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollIntervalSeconds").value(300));
    }

    @Test
    void putUpdatesTheIntervalAndItIsReflectedOnTheNextGet() throws Exception {
        mockMvc.perform(put("/api/ingestion-settings")
                        .contentType("application/json")
                        .content("{\"pollIntervalSeconds\": 120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollIntervalSeconds").value(120));

        mockMvc.perform(get("/api/ingestion-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollIntervalSeconds").value(120));
    }

    @Test
    void putRejectsAnIntervalBelowTheMinimum() throws Exception {
        mockMvc.perform(put("/api/ingestion-settings")
                        .contentType("application/json")
                        .content("{\"pollIntervalSeconds\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("pollIntervalSeconds must be at least 60"));
    }
}
