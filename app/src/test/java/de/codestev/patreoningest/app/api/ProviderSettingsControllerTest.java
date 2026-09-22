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

// Application.class pulls in ingest-providers-default's real parsers via
// component scanning, so ProviderSettingsSeeder has already seeded
// nomnom/bulkamancer/wicked (all MANUAL) by the time this context is up -
// no manual fixture setup needed for the GET tests.
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ProviderSettingsControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsEverySeededProviderDefaultingToManual() throws Exception {
        mockMvc.perform(get("/api/provider-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.providerId=='nomnom')].downloadPolicy").value("MANUAL"))
                .andExpect(jsonPath("$[?(@.providerId=='bulkamancer')].downloadPolicy").value("MANUAL"))
                .andExpect(jsonPath("$[?(@.providerId=='wicked')].downloadPolicy").value("MANUAL"));
    }

    @Test
    void putUpdatesAProvidersPolicy() throws Exception {
        mockMvc.perform(put("/api/provider-settings/nomnom")
                        .contentType("application/json")
                        .content("{\"downloadPolicy\": \"EAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value("nomnom"))
                .andExpect(jsonPath("$.downloadPolicy").value("EAGER"));

        mockMvc.perform(get("/api/provider-settings"))
                .andExpect(jsonPath("$[?(@.providerId=='nomnom')].downloadPolicy").value("EAGER"));
    }

    @Test
    void putOnAnUnknownProviderReturns404() throws Exception {
        mockMvc.perform(put("/api/provider-settings/does-not-exist")
                        .contentType("application/json")
                        .content("{\"downloadPolicy\": \"EAGER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void putWithoutADownloadPolicyReturns400() throws Exception {
        mockMvc.perform(put("/api/provider-settings/nomnom")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
