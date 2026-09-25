package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.TestApplication;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Import(ClaimQueueJobTest.TestConfig.class)
@Transactional
class ClaimQueueJobTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ClaimQueueJob claimQueueJob;

    @Autowired
    private DownloadSourceRepository downloadSourceRepository;

    @Autowired
    private FakeClaimPort fakeClaimPort;

    @TestConfiguration
    static class TestConfig {
        @Bean
        FakeClaimPort fakeClaimPort() {
            return new FakeClaimPort();
        }
    }

    @BeforeEach
    void resetFake() {
        fakeClaimPort.reset();
    }

    private DownloadSource gumroadSource(String url) {
        return downloadSourceRepository.save(new DownloadSource("wicked", null, null,
                SourceType.GUMROAD, url, ClaimType.GUMROAD));
    }

    private DownloadSource reload(DownloadSource source) {
        return downloadSourceRepository.findById(source.getId()).orElseThrow();
    }

    @Test
    void aSuccessfulClaimMarksTheSourceClaimedWithItsReceipt() {
        DownloadSource source = gumroadSource("https://wicked.gumroad.com/l/a/code");
        fakeClaimPort.willReturn(new ClaimOutcome.Claimed("https://gumroad.com/d/abc"));

        claimQueueJob.runDue();

        DownloadSource claimed = reload(source);
        assertThat(claimed.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(claimed.getClaimedAt()).isNotNull();
        assertThat(claimed.getClaimReceiptUrl()).isEqualTo("https://gumroad.com/d/abc");
    }

    @Test
    void anAlreadyOwnedSourceIsClaimedWithoutAReceipt() {
        DownloadSource source = gumroadSource("https://wicked.gumroad.com/l/a/code");
        fakeClaimPort.willReturn(new ClaimOutcome.AlreadyOwned());

        claimQueueJob.runDue();

        DownloadSource owned = reload(source);
        assertThat(owned.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(owned.getClaimReceiptUrl()).isNull();
        assertThat(owned.getClaimNote()).isEqualTo(DownloadSource.ALREADY_OWNED_NOTE);
    }

    @Test
    void needsManualHandsTheSourceToTheOperatorWithoutRetrying() {
        DownloadSource source = gumroadSource("https://wicked.gumroad.com/l/a/code");
        fakeClaimPort.willReturn(new ClaimOutcome.NeedsManual("Bot-check challenge shown"));

        claimQueueJob.runDue();
        claimQueueJob.runDue();

        DownloadSource manual = reload(source);
        assertThat(manual.getClaimStatus()).isEqualTo(ClaimStatus.NEEDS_MANUAL);
        assertThat(manual.getClaimNote()).isEqualTo("Bot-check challenge shown");
        assertThat(fakeClaimPort.claimedUrls()).hasSize(1);
    }

    @Test
    void aTransientFailureIsRetriedLaterNotImmediately() {
        DownloadSource source = gumroadSource("https://wicked.gumroad.com/l/a/code");
        fakeClaimPort.willReturn(new ClaimOutcome.RetryLater("Coupon did not apply in time"));

        claimQueueJob.runDue();
        claimQueueJob.runDue();

        DownloadSource retrying = reload(source);
        assertThat(retrying.getClaimStatus()).isEqualTo(ClaimStatus.DISCOVERED);
        assertThat(retrying.getClaimAttempts()).isEqualTo(1);
        assertThat(retrying.getNextClaimAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(fakeClaimPort.claimedUrls()).hasSize(1);
    }

    @Test
    void anExceptionCountsAsATransientFailureAndDoesNotStopTheQueue() {
        DownloadSource first = gumroadSource("https://wicked.gumroad.com/l/a/code");
        DownloadSource second = gumroadSource("https://wicked.gumroad.com/l/b/code");
        fakeClaimPort.willThrow(new IllegalStateException("browser crashed"));
        fakeClaimPort.willReturn(new ClaimOutcome.Claimed(null));

        claimQueueJob.runDue();

        assertThat(reload(first).getClaimStatus()).isEqualTo(ClaimStatus.DISCOVERED);
        assertThat(reload(first).getClaimNote()).contains("browser crashed");
        assertThat(reload(second).getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED);
    }

    @Test
    void repeatedFailuresEventuallyNeedManualAction() {
        DownloadSource source = gumroadSource("https://wicked.gumroad.com/l/a/code");
        for (int i = 1; i < ClaimQueueJob.MAX_ATTEMPTS; i++) {
            source.markClaimAttemptFailed("earlier failure", LocalDateTime.now().minusMinutes(1));
        }
        downloadSourceRepository.save(source);
        fakeClaimPort.willReturn(new ClaimOutcome.RetryLater("still failing"));

        claimQueueJob.runDue();

        DownloadSource gaveUp = reload(source);
        assertThat(gaveUp.getClaimStatus()).isEqualTo(ClaimStatus.NEEDS_MANUAL);
        assertThat(gaveUp.getClaimNote()).contains("still failing");
    }

    @Test
    void implicitlyClaimedSourcesAreNeverSentToAPort() {
        DownloadSource drive = new DownloadSource("nomnom", null, null,
                SourceType.DRIVE, "https://drive.google.com/drive/folders/x", ClaimType.NONE);
        downloadSourceRepository.save(drive);

        claimQueueJob.runDue();

        assertThat(fakeClaimPort.claimedUrls()).isEmpty();
    }
}
