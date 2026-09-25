package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Transactional
class MarkClaimedManuallyUseCaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MarkClaimedManuallyUseCase markClaimedManuallyUseCase;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Test
    void aSourceNeedingManualActionBecomesClaimed() {
        DownloadSource source = new DownloadSource("wicked", null, null,
                SourceType.GUMROAD, "https://wicked.gumroad.com/l/a/code", ClaimType.GUMROAD);
        source.markNeedsManualClaim("Bot-check challenge shown");
        downloadSourceRepository.save(source);

        MarkClaimedManuallyUseCase.Result result = markClaimedManuallyUseCase.markClaimed(source.getId());

        assertThat(result).isEqualTo(MarkClaimedManuallyUseCase.Result.MARKED_CLAIMED);
        DownloadSource claimed = downloadSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(claimed.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(claimed.getClaimNote()).isNull();
    }

    @Test
    void anUnknownSourceIsNotFound() {
        assertThat(markClaimedManuallyUseCase.markClaimed(UUID.randomUUID()))
                .isEqualTo(MarkClaimedManuallyUseCase.Result.NOT_FOUND);
    }
}
