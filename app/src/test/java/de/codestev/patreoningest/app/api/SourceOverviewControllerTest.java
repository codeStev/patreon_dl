package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.app.Application;
import de.codestev.patreoningest.core.acquisition.ClaimType;
import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.DownloadItemRepository;
import de.codestev.patreoningest.core.acquisition.DownloadSource;
import de.codestev.patreoningest.core.acquisition.DownloadSourceRepository;
import de.codestev.patreoningest.core.acquisition.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class SourceOverviewControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    @Test
    void listsSourcesWithTheirItemsAndStatus() throws Exception {
        DownloadSource source = new DownloadSource("nomnom", null, null,
                SourceType.DRIVE, "https://drive.example/folder/overview-test", ClaimType.NONE);
        source.markClaimed();
        downloadSourceRepository.save(source);
        downloadItemRepository.save(new DownloadItem(source, "Wolverine", null));

        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].creator").value("nomnom"))
                .andExpect(jsonPath("$[0].sourceType").value("DRIVE"))
                .andExpect(jsonPath("$[0].claimStatus").value("CLAIMED"))
                .andExpect(jsonPath("$[0].items[0].modelName").value("Wolverine"))
                .andExpect(jsonPath("$[0].items[0].status").value("PENDING"));
    }

    @Test
    void aSourceNeedingAManualClaimCanBeMarkedClaimed() throws Exception {
        DownloadSource source = new DownloadSource("wicked", null, null,
                SourceType.GUMROAD, "https://wicked.gumroad.com/l/overview-test/code", ClaimType.GUMROAD);
        source.markNeedsManualClaim("Gumroad showed a reCAPTCHA challenge - claim it by hand");
        downloadSourceRepository.save(source);

        mockMvc.perform(get("/api/sources"))
                .andExpect(jsonPath("$[0].claimStatus").value("NEEDS_MANUAL"))
                .andExpect(jsonPath("$[0].claimNote").value("Gumroad showed a reCAPTCHA challenge - claim it by hand"));

        mockMvc.perform(post("/api/sources/{id}/mark-claimed", source.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sources/{id}/mark-claimed", source.getId()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sources/{id}/mark-claimed", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSourceWithNoItemsHasAnEmptyItemsList() throws Exception {
        DownloadSource source = new DownloadSource("nomnom", null, null,
                SourceType.DRIVE, "https://drive.example/folder/no-items", ClaimType.NONE);
        downloadSourceRepository.save(source);

        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items").isArray())
                .andExpect(jsonPath("$[0].items").isEmpty());
    }

    @Test
    void aDriveLinkCanBeAddedByHandAndItsCollectionsAreListedWithTheirGroup() throws Exception {
        String body = """
                {"name": "my-archive", "url": "https://drive.google.com/drive/folders/rolling-api-1",
                 "layout": "COLLECTIONS"}
                """;

        mockMvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creator").value("my-archive"))
                .andExpect(jsonPath("$.sourceType").value("DRIVE"))
                .andExpect(jsonPath("$.folderLayout").value("COLLECTIONS"))
                .andExpect(jsonPath("$.claimStatus").value("CLAIMED"));

        DownloadSource source = downloadSourceRepository
                .findByCreatorAndSourceUrl("my-archive", "https://drive.google.com/drive/folders/rolling-api-1")
                .orElseThrow();
        downloadItemRepository.save(new DownloadItem(source, "2025-01 Release", "collection-1", true, "Titan Forge"));

        mockMvc.perform(get("/api/sources"))
                .andExpect(jsonPath("$[0].items[0].groupName").value("Titan Forge"))
                .andExpect(jsonPath("$[0].items[0].modelName").value("2025-01 Release"));
    }

    @Test
    void addingALinkWithoutALayoutDefaultsToModels() throws Exception {
        mockMvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "loose-files", "url": "https://drive.google.com/drive/folders/flat-api-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folderLayout").value("MODELS"));
    }

    @Test
    void anInvalidLinkIsRejectedWithAReason() throws Exception {
        mockMvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "my-archive", "url": "https://example.com/nope", "layout": "COLLECTIONS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not a Google Drive folder or file link"));

        mockMvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "nomnom", "url": "https://drive.google.com/drive/folders/x1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That name belongs to an email provider - pick another"));
    }

    @Test
    void aHandAddedLinkCanBeRemovedButAnEmailParsedSourceCannot() throws Exception {
        mockMvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "my-archive", "url": "https://drive.google.com/drive/folders/remove-api-1"}
                                """))
                .andExpect(jsonPath("$.addedManually").value(true));
        DownloadSource added = downloadSourceRepository
                .findByCreatorAndSourceUrl("my-archive", "https://drive.google.com/drive/folders/remove-api-1")
                .orElseThrow();
        DownloadSource parsed = downloadSourceRepository.save(new DownloadSource("nomnom", null, null,
                SourceType.DRIVE, "https://drive.example/folder/parsed", ClaimType.NONE));

        mockMvc.perform(get("/api/sources"))
                .andExpect(jsonPath("$[?(@.creator == 'nomnom')].addedManually").value(false));

        mockMvc.perform(delete("/api/sources/{id}", added.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/sources/{id}", added.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/sources/{id}", parsed.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only links added by hand can be removed"));
    }
}
