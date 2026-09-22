package de.codestev.patreoningest.core.ingestion;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Non-static (fresh GreenMail server per test method) so each test's UID
// sequence starts clean - the whole point here is asserting on specific UIDs.
class ImapMailboxAdapterTest {

    @RegisterExtension
    GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("testuser@localhost", "password"));

    private final ProcessedEmailRepository processedEmailRepository = mock(ProcessedEmailRepository.class);

    @Test
    void theFirstEverPollForAMailboxScansWhateverIsAlreadyThere() {
        GreenMailUtil.sendTextEmailTest("testuser@localhost", "updates@nomnom.example", "New models",
                "[JULY MODELS]\n- Cool Dragon\nhttps://drive.google.com/drive/folders/greenmail\n");
        when(processedEmailRepository.findMaxUidByMailbox("INBOX")).thenReturn(Optional.empty());

        List<EmailMessage> messages = adapter().fetchNewMessages();

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).fromAddress()).isEqualTo("updates@nomnom.example");
    }

    @Test
    void onlyMessagesNewerThanTheRecordedUidAreFetched() {
        GreenMailUtil.sendTextEmailTest("testuser@localhost", "first@example.com", "Old message", "old body");
        // UIDs are assigned sequentially starting at 1 - simulates "we've
        // already recorded the first message" without needing a real DB.
        when(processedEmailRepository.findMaxUidByMailbox("INBOX")).thenReturn(Optional.of(1L));

        GreenMailUtil.sendTextEmailTest("testuser@localhost", "updates@nomnom.example", "New models",
                "[JULY MODELS]\n- Cool Dragon\nhttps://drive.google.com/drive/folders/greenmail\n");

        List<EmailMessage> messages = adapter().fetchNewMessages();

        assertThat(messages).hasSize(1);
        EmailMessage message = messages.get(0);
        assertThat(message.mailbox()).isEqualTo("INBOX");
        assertThat(message.fromAddress()).isEqualTo("updates@nomnom.example");
        assertThat(message.subject()).isEqualTo("New models");
        assertThat(message.body()).contains("https://drive.google.com/drive/folders/greenmail");
    }

    private ImapMailboxAdapter adapter() {
        ImapProperties properties = new ImapProperties(
                "localhost", ServerSetupTest.IMAP.getPort(), "testuser@localhost", "password", "INBOX", false);
        return new ImapMailboxAdapter(properties, processedEmailRepository);
    }
}
