package de.codestev.patreoningest.core.ingestion;

import de.codestev.patreoningest.core.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// Regression coverage for a real production bug: docker-compose's
// `${VAR:-}` substitution always sets the env var key, just to an empty
// string when unset in .env - a plain @ConditionalOnProperty treats that as
// "configured" and tried to connect to an empty host, crashing the
// scheduler. ImapMailboxAdapter's custom condition must treat blank as
// absent.
class ImapMailboxAdapterActivationTest {

    @Testcontainers
    @SpringBootTest(classes = TestApplication.class, properties = "patreon.imap.host=")
    static class BlankHost {

        @Container
        @ServiceConnection
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

        @Autowired
        private MailboxPort mailboxPort;

        @Test
        void blankHostFallsBackToTheNoOpAdapter() {
            assertThat(mailboxPort).isNotInstanceOf(ImapMailboxAdapter.class);
        }
    }

    @Testcontainers
    @SpringBootTest(classes = TestApplication.class, properties = {
            "patreon.imap.host=imap.example.com",
            "patreon.imap.username=someone",
            "patreon.imap.password=secret"
    })
    static class ConfiguredHost {

        @Container
        @ServiceConnection
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

        @Autowired
        private MailboxPort mailboxPort;

        @Test
        void nonBlankHostActivatesTheRealAdapter() {
            assertThat(mailboxPort).isInstanceOf(ImapMailboxAdapter.class);
        }
    }
}
