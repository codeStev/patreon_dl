package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.TestApplication;
import de.codestev.patreoningest.core.ingestion.ParsedItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Transactional
class RegisterParsedItemsUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RegisterParsedItemsUseCase registerParsedItemsUseCase;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    @Autowired
    private ProviderSettingsRepository providerSettingsRepository;

    private static final ParsedItem NOMNOM_JULY = new ParsedItem(
            "nomnom", "regular", "JULY", SourceType.DRIVE,
            "https://drive.example/folder/july", ClaimType.NONE, null);

    @Test
    void firstRegistrationCreatesAClaimedSourceWithNoClaimPortRegistered() {
        registerParsedItemsUseCase.register(List.of(NOMNOM_JULY));

        DownloadSource source = downloadSourceRepository
                .findByCreatorAndSourceUrl("nomnom", "https://drive.example/folder/july")
                .orElseThrow();

        assertThat(source.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(source.getClaimedAt()).isNotNull();
        assertThat(source.getCategory()).isEqualTo("regular");
        assertThat(source.getMonthLabel()).isEqualTo("JULY");
    }

    @Test
    void reRegisteringTheSameSourceUrlIsANoOp() {
        registerParsedItemsUseCase.register(List.of(NOMNOM_JULY));
        long countAfterFirst = downloadSourceRepository.count();

        registerParsedItemsUseCase.register(List.of(NOMNOM_JULY));
        long countAfterSecond = downloadSourceRepository.count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);

        DownloadSource source = downloadSourceRepository
                .findByCreatorAndSourceUrl("nomnom", "https://drive.example/folder/july")
                .orElseThrow();
        assertThat(source.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
    }

    @Test
    void aNamedItemGetsItsOwnDownloadItemRow() {
        ParsedItem bulkamancerWolverine = new ParsedItem("bulkamancer", null, null, SourceType.DRIVE,
                "https://drive.example/folder/wolverine", ClaimType.NONE, "Wolverine");

        registerParsedItemsUseCase.register(List.of(bulkamancerWolverine));

        DownloadSource source = downloadSourceRepository
                .findByCreatorAndSourceUrl("bulkamancer", "https://drive.example/folder/wolverine")
                .orElseThrow();
        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .extracting(DownloadItem::getModelName)
                .containsExactly("Wolverine");
    }

    @Test
    void aWholeFolderItemWithNoModelNameGetsNoDownloadItemRow() {
        registerParsedItemsUseCase.register(List.of(NOMNOM_JULY));

        DownloadSource source = downloadSourceRepository
                .findByCreatorAndSourceUrl("nomnom", "https://drive.example/folder/july")
                .orElseThrow();
        assertThat(downloadItemRepository.findBySourceId(source.getId())).isEmpty();
    }

    @Test
    void aGumroadItemStaysDiscoveredSinceNoClaimPortExistsYet() {
        ParsedItem wickedItem = new ParsedItem("wicked", null, null, SourceType.GUMROAD,
                "https://wicked.gumroad.com/l/example", ClaimType.GUMROAD, "Example Model");

        registerParsedItemsUseCase.register(List.of(wickedItem));

        DownloadSource source = downloadSourceRepository
                .findByCreatorAndSourceUrl("wicked", "https://wicked.gumroad.com/l/example")
                .orElseThrow();
        assertThat(source.getClaimStatus()).isEqualTo(ClaimStatus.DISCOVERED);
        assertThat(source.getClaimedAt()).isNull();
    }

    @Test
    void aDisabledProviderGetsNoSourceAtAll() {
        providerSettingsRepository.save(new ProviderSettings("nomnom", DownloadPolicy.DISABLED));

        registerParsedItemsUseCase.register(List.of(NOMNOM_JULY));

        assertThat(downloadSourceRepository.findByCreatorAndSourceUrl(
                "nomnom", "https://drive.example/folder/july")).isEmpty();
    }
}
