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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Import(AddManualSourceUseCaseTest.TestConfig.class)
@Transactional
class AddManualSourceUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final String FOLDER_URL = "https://drive.google.com/drive/folders/rolling-folder-1";
    private static final String FILE_URL = "https://drive.google.com/file/d/some-file-1/view";

    @Autowired
    private AddManualSourceUseCase addManualSourceUseCase;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private ProviderSettingsRepository providerSettingsRepository;

    @TestConfiguration
    static class TestConfig {
        // Stands in for a real provider parser, to check its id is reserved.
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

    @Test
    void addsAClaimedDriveSourceWithItsOwnManualProviderSettings() {
        AddManualSourceUseCase.Result result =
                addManualSourceUseCase.add("  my-archive ", " " + FOLDER_URL + " ", FolderLayout.COLLECTIONS);

        assertThat(result.outcome()).isEqualTo(AddManualSourceUseCase.Outcome.ADDED);
        DownloadSource source = downloadSourceRepository.findByCreatorAndSourceUrl("my-archive", FOLDER_URL)
                .orElseThrow();
        assertThat(source.getId()).isEqualTo(result.source().getId());
        assertThat(source.getSourceType()).isEqualTo(SourceType.DRIVE);
        assertThat(source.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(source.getFolderLayout()).isEqualTo(FolderLayout.COLLECTIONS);
        assertThat(providerSettingsRepository.findById("my-archive"))
                .get()
                .extracting(ProviderSettings::getDownloadPolicy)
                .isEqualTo(DownloadPolicy.MANUAL);
    }

    @Test
    void aSecondLinkUnderTheSameNameKeepsTheExistingPolicy() {
        addManualSourceUseCase.add("my-archive", FOLDER_URL, FolderLayout.COLLECTIONS);
        ProviderSettings settings = providerSettingsRepository.findById("my-archive").orElseThrow();
        settings.updatePolicy(DownloadPolicy.EAGER);
        providerSettingsRepository.save(settings);

        AddManualSourceUseCase.Result second = addManualSourceUseCase.add(
                "my-archive", "https://drive.google.com/drive/folders/rolling-folder-2", FolderLayout.MODELS);

        assertThat(second.outcome()).isEqualTo(AddManualSourceUseCase.Outcome.ADDED);
        assertThat(providerSettingsRepository.findById("my-archive").orElseThrow().getDownloadPolicy())
                .isEqualTo(DownloadPolicy.EAGER);
    }

    @Test
    void aSingleFileLinkIsAcceptedAsModels() {
        assertThat(addManualSourceUseCase.add("one-file", FILE_URL, FolderLayout.MODELS).outcome())
                .isEqualTo(AddManualSourceUseCase.Outcome.ADDED);
    }

    @Test
    void rejectsWhatItCannotSync() {
        assertThat(addManualSourceUseCase.add("my-archive", FOLDER_URL, FolderLayout.COLLECTIONS).outcome())
                .isEqualTo(AddManualSourceUseCase.Outcome.ADDED);

        assertThat(outcomeOf("my-archive", FOLDER_URL, FolderLayout.COLLECTIONS))
                .isEqualTo(AddManualSourceUseCase.Outcome.ALREADY_EXISTS);
        assertThat(outcomeOf("my-archive", "https://example.com/not-drive", FolderLayout.MODELS))
                .isEqualTo(AddManualSourceUseCase.Outcome.NOT_A_DRIVE_LINK);
        assertThat(outcomeOf("my-archive", FILE_URL, FolderLayout.COLLECTIONS))
                .isEqualTo(AddManualSourceUseCase.Outcome.COLLECTIONS_NEED_A_FOLDER);
        assertThat(outcomeOf("nomnom", FOLDER_URL, FolderLayout.MODELS))
                .isEqualTo(AddManualSourceUseCase.Outcome.RESERVED_NAME);
        for (String badName : List.of("", "   ", "a/b", "..", "x".repeat(101))) {
            assertThat(outcomeOf(badName, FOLDER_URL, FolderLayout.MODELS))
                    .as("name %s", badName)
                    .isEqualTo(AddManualSourceUseCase.Outcome.INVALID_NAME);
        }
        assertThat(downloadSourceRepository.count()).isEqualTo(1);
    }

    private AddManualSourceUseCase.Outcome outcomeOf(String name, String url, FolderLayout layout) {
        return addManualSourceUseCase.add(name, url, layout).outcome();
    }
}
