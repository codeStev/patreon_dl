package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Transactional
class AcquisitionPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private DownloadItemRepository downloadItemRepository;

    @Autowired
    private ProviderSettingsRepository providerSettingsRepository;

    @Test
    void savesAndFindsDownloadSource() {
        DownloadSource source = new DownloadSource(
                "nomnom", "regular", "2026-09", SourceType.DRIVE,
                "https://drive.example/folder/abc", ClaimType.NONE);

        downloadSourceRepository.saveAndFlush(source);

        Optional<DownloadSource> found = downloadSourceRepository.findByCreatorAndSourceUrl(
                "nomnom", "https://drive.example/folder/abc");

        assertThat(found).isPresent();
        assertThat(found.get().getClaimStatus()).isEqualTo(ClaimStatus.DISCOVERED);
    }

    @Test
    void rejectsDuplicateCreatorAndSourceUrl() {
        downloadSourceRepository.saveAndFlush(new DownloadSource(
                "nomnom", "regular", "2026-09", SourceType.DRIVE,
                "https://drive.example/folder/dup", ClaimType.NONE));

        DownloadSource duplicate = new DownloadSource(
                "nomnom", "extra", "2026-09", SourceType.DRIVE,
                "https://drive.example/folder/dup", ClaimType.NONE);

        assertThatThrownBy(() -> downloadSourceRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesAndFindsDownloadItemWithRoundTrip() {
        DownloadSource source = downloadSourceRepository.saveAndFlush(new DownloadSource(
                "nomnom", "regular", "2026-09", SourceType.DRIVE,
                "https://drive.example/folder/items", ClaimType.NONE));

        DownloadItem item = new DownloadItem(source, "cool-model.stl", "drive-file-1");
        downloadItemRepository.saveAndFlush(item);

        assertThat(downloadItemRepository.findBySourceId(source.getId()))
                .hasSize(1)
                .first()
                .satisfies(found -> {
                    assertThat(found.getModelName()).isEqualTo("cool-model.stl");
                    assertThat(found.getStatus()).isEqualTo(ItemStatus.PENDING);
                });
    }

    @Test
    void rejectsDuplicateSourceAndRemoteFileId() {
        DownloadSource source = downloadSourceRepository.saveAndFlush(new DownloadSource(
                "nomnom", "regular", "2026-09", SourceType.DRIVE,
                "https://drive.example/folder/dedup", ClaimType.NONE));

        downloadItemRepository.saveAndFlush(new DownloadItem(source, "model-a.stl", "drive-file-dup"));

        DownloadItem duplicate = new DownloadItem(source, "model-a-again.stl", "drive-file-dup");

        assertThatThrownBy(() -> downloadItemRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roundTripsFolderLayoutAndGroupName() {
        DownloadSource legacy = downloadSourceRepository.saveAndFlush(new DownloadSource(
                "nomnom", null, null, SourceType.DRIVE,
                "https://drive.example/folder/legacy", ClaimType.NONE));
        DownloadSource rolling = downloadSourceRepository.saveAndFlush(new DownloadSource(
                "my-archive", null, null, SourceType.DRIVE,
                "https://drive.example/folder/rolling", ClaimType.NONE, FolderLayout.COLLECTIONS));
        downloadItemRepository.saveAndFlush(
                new DownloadItem(rolling, "2025-01 Release", "collection-1", true, "Titan Forge"));

        assertThat(downloadSourceRepository.findById(legacy.getId()).orElseThrow().getFolderLayout())
                .isEqualTo(FolderLayout.MODELS);
        assertThat(downloadSourceRepository.findById(rolling.getId()).orElseThrow().getFolderLayout())
                .isEqualTo(FolderLayout.COLLECTIONS);
        assertThat(downloadItemRepository.findBySourceId(rolling.getId()))
                .extracting(DownloadItem::getGroupName)
                .containsExactly("Titan Forge");
    }

    @Test
    void savesAndFindsProviderSettings() {
        providerSettingsRepository.saveAndFlush(new ProviderSettings("wicked", DownloadPolicy.MANUAL));

        Optional<ProviderSettings> found = providerSettingsRepository.findById("wicked");

        assertThat(found).isPresent();
        assertThat(found.get().getDownloadPolicy()).isEqualTo(DownloadPolicy.MANUAL);
    }
}
