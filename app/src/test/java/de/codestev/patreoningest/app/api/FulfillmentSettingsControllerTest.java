package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.app.Application;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class FulfillmentSettingsControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getReturnsTheSeededDefaults() throws Exception {
        mockMvc.perform(get("/api/fulfillment-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxConcurrentDownloads").value(1))
                .andExpect(jsonPath("$.ioNice").value(true))
                .andExpect(jsonPath("$.bandwidthLimitKbps").doesNotExist());
    }

    @Test
    void putUpdatesSettingsAndTheyAreReflectedOnTheNextGet() throws Exception {
        mockMvc.perform(put("/api/fulfillment-settings")
                        .contentType("application/json")
                        .content("""
                                {"maxConcurrentDownloads": 2, "bandwidthLimitKbps": 500, "ioNice": false,
                                 "allowedHoursStart": "22:00:00", "allowedHoursEnd": "06:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxConcurrentDownloads").value(2))
                .andExpect(jsonPath("$.bandwidthLimitKbps").value(500));

        mockMvc.perform(get("/api/fulfillment-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxConcurrentDownloads").value(2))
                .andExpect(jsonPath("$.allowedHoursStart").value("22:00:00"));
    }

    @Test
    void putRejectsAMaxConcurrentDownloadsBelowOne() throws Exception {
        mockMvc.perform(put("/api/fulfillment-settings")
                        .contentType("application/json")
                        .content("""
                                {"maxConcurrentDownloads": 0, "ioNice": true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("maxConcurrentDownloads must be at least 1"));
    }

    @Test
    void putRejectsAnAllowedHoursWindowWithOnlyOneEndSet() throws Exception {
        mockMvc.perform(put("/api/fulfillment-settings")
                        .contentType("application/json")
                        .content("""
                                {"maxConcurrentDownloads": 1, "ioNice": true, "allowedHoursStart": "22:00:00"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
