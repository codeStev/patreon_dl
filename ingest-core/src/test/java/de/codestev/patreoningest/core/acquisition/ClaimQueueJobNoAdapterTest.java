package de.codestev.patreoningest.core.acquisition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// No ClaimPort registered at all - the "GUMROAD_EMAIL not set" case. The
// sources must stay untouched, but the operator has to be told why.
@ExtendWith(OutputCaptureExtension.class)
class ClaimQueueJobNoAdapterTest {

    private final DownloadSourceRepository downloadSourceRepository = mock(DownloadSourceRepository.class);
    private final ProviderSettingsRepository providerSettingsRepository = mock(ProviderSettingsRepository.class);
    private final ClaimQueueJob claimQueueJob =
            new ClaimQueueJob(List.of(), downloadSourceRepository, providerSettingsRepository);

    private DownloadSource gumroadSource(String url) {
        return new DownloadSource("wicked", null, null, SourceType.GUMROAD, url, ClaimType.GUMROAD);
    }

    @Test
    void dueClaimsWithoutAnAdapterAreLeftAloneButWarnedAboutOnce(CapturedOutput output) {
        when(providerSettingsRepository.findById("wicked")).thenReturn(Optional.empty());
        when(downloadSourceRepository.findClaimsDue(any())).thenReturn(List.of(
                gumroadSource("https://wicked.gumroad.com/l/a/code"),
                gumroadSource("https://wicked.gumroad.com/l/b/code")));

        claimQueueJob.runDue();
        claimQueueJob.runDue();

        verify(downloadSourceRepository, never()).save(any());
        assertThat(output.getOut()).containsOnlyOnce("No claim adapter is active for GUMROAD");
        assertThat(output.getOut()).contains("2 due claim(s)");
    }
}
