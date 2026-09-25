package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.TestApplication;
import de.codestev.patreoningest.core.ingestion.CreatorMessageParser;
import de.codestev.patreoningest.core.ingestion.ParsedItem;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Import(RemoveManualSourceUseCaseTest.TestConfig.class)
@Transactional
class RemoveManualSourceUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RemoveManualSourceUseCase removeManualSourceUseCase;

    @Autowired
    private AddManualSourceUseCase addManualSourceUseCase;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    @Autowired
    private ProviderSettingsRepository providerSettingsRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        CreatorMessageParser nomnomParser() {
            return new CreatorMessageParser() {
                @Override
                public String providerId() {
                    return "nomnom";
                }

                @Override
                public boolean supports(String fromAddress, String subject) {
                    return false;
                }

                @Override
                public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
                    return List.of();
                }
            };
        }
    }

    private DownloadSource addLink(String name, String folderId) {
        return addManualSourceUseCase.add(name, "https://drive.google.com/drive/folders/" + folderId,
                FolderLayout.COLLECTIONS).source();
    }

    @Test
    void removesTheSourceItsItemsAndTheNamesSettingsWithTheLastLink() {
        DownloadSource source = addLink("my-archive", "remove-1");
        downloadItemRepository.save(new DownloadItem(source, "2025-01 Release", "collection-1", true, "Titan Forge"));

        assertThat(removeManualSourceUseCase.remove(source.getId()))
                .isEqualTo(RemoveManualSourceUseCase.Result.REMOVED);

        assertThat(downloadSourceRepository.findById(source.getId())).isEmpty();
        assertThat(downloadItemRepository.findBySourceId(source.getId())).isEmpty();
        assertThat(providerSettingsRepository.findById("my-archive")).isEmpty();
    }

    @Test
    void keepsTheNamesSettingsWhileAnotherLinkStillUsesIt() {
        DownloadSource first = addLink("my-archive", "remove-2a");
        addLink("my-archive", "remove-2b");

        removeManualSourceUseCase.remove(first.getId());

        assertThat(providerSettingsRepository.findById("my-archive")).isPresent();
    }

    @Test
    void refusesAnEmailParsedSource() {
        DownloadSource parsed = downloadSourceRepository.save(new DownloadSource("nomnom", null, "AUGUST",
                SourceType.DRIVE, "https://drive.google.com/drive/folders/nomnom-aug", ClaimType.NONE));

        assertThat(removeManualSourceUseCase.remove(parsed.getId()))
                .isEqualTo(RemoveManualSourceUseCase.Result.NOT_ADDED_MANUALLY);
        assertThat(downloadSourceRepository.findById(parsed.getId())).isPresent();
    }

    @Test
    void anUnknownIdIsNotFound() {
        assertThat(removeManualSourceUseCase.remove(UUID.randomUUID()))
                .isEqualTo(RemoveManualSourceUseCase.Result.NOT_FOUND);
    }
}
