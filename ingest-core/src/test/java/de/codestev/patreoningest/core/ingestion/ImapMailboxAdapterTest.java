package de.codestev.patreoningest.core.ingestion;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImapMailboxAdapterTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("testuser@localhost", "password"));

    @Test
    void fetchesAMessageSentIntoTheMailboxViaRealImap() {
        GreenMailUtil.sendTextEmailTest("testuser@localhost", "updates@nomnom.example", "New models",
                "[JULY MODELS]\n- Cool Dragon\nhttps://drive.google.com/drive/folders/greenmail\n");

        ImapProperties properties = new ImapProperties(
                "localhost", ServerSetupTest.IMAP.getPort(), "testuser@localhost", "password", "INBOX", false);
        ImapMailboxAdapter adapter = new ImapMailboxAdapter(properties);

        List<EmailMessage> messages = adapter.fetchNewMessages();

        assertThat(messages).hasSize(1);
        EmailMessage message = messages.get(0);
        assertThat(message.mailbox()).isEqualTo("INBOX");
        assertThat(message.fromAddress()).isEqualTo("updates@nomnom.example");
        assertThat(message.subject()).isEqualTo("New models");
        assertThat(message.body()).contains("https://drive.google.com/drive/folders/greenmail");
    }
}
