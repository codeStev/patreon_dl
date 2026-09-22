package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.app.Application;
import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import de.codestev.patreoningest.core.acquisition.ItemStatus;
import de.codestev.patreoningest.core.acquisition.SourceType;
import de.codestev.patreoningest.core.fulfillment.DownloadResult;
import de.codestev.patreoningest.core.fulfillment.DownloadRuntimeOptions;
import de.codestev.patreoningest.core.fulfillment.RcloneDriveDownloadAdapter;
import de.codestev.patreoningest.core.fulfillment.SourceDownloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Deliberately NOT @Transactional: a dispatched download runs on a
// separate thread/connection that would never see fixture rows stuck in an
// uncommitted test transaction (see the ingest-core Fulfillment use-case
// tests for the same reasoning). Cleaned up manually instead.
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(DownloadItemControllerTest.TestConfig.class)
class DownloadItemControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    // Neutralizes the real adapter in the shared List<SourceDownloader> -
    // an unstubbed mock's supports() returns null, never DRIVE.
    @MockitoBean
    private RcloneDriveDownloadAdapter rcloneDriveDownloadAdapter;

    @TestConfiguration
    static class TestConfig {
        @Bean
        SourceDownloader alwaysSucceedingDownloader() {
            return new SourceDownloader() {
                @Override
                public SourceType supports() {
                    return SourceType.DRIVE;
                }

                @Override
                public DownloadResult fetch(DownloadItem item, Path targetDir, DownloadRuntimeOptions options) {
                    return new DownloadResult("/downloads/wicked/Model", 1L);
                }
            };
        }
    }

    @AfterEach
    void cleanUp() {
        downloadItemRepository.deleteAll();
        downloadSourceRepository.deleteAll();
    }

    private DownloadItem claimedPendingItem() {
        DownloadSource source = new DownloadSource("wicked", null, null,
                SourceType.DRIVE, "https://drive.example/api-trigger", ClaimType.NONE);
        source.markClaimed();
        downloadSourceRepository.save(source);
        return downloadItemRepository.save(new DownloadItem(source, "Model", null));
    }

    @Test
    void triggeringAnEligibleItemReturns202AndEventuallyDownloadsIt() throws Exception {
        DownloadItem item = claimedPendingItem();

        mockMvc.perform(post("/api/download-items/{id}/download-now", item.getId()))
                .andExpect(status().isAccepted());

        await().untilAsserted(() ->
                assertThat(downloadItemRepository.findById(item.getId()).orElseThrow().getStatus())
                        .isEqualTo(ItemStatus.DOWNLOADED));
    }

    @Test
    void triggeringAMissingItemReturns404() throws Exception {
        mockMvc.perform(post("/api/download-items/{id}/download-now", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void triggeringAnAlreadyDownloadedItemReturns400() throws Exception {
        DownloadItem item = claimedPendingItem();
        item.markDownloaded("/downloads/wicked/Model", 1L);
        downloadItemRepository.save(item);

        mockMvc.perform(post("/api/download-items/{id}/download-now", item.getId()))
                .andExpect(status().isBadRequest());
    }
}
