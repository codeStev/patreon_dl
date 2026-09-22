package de.codestev.patreoningest.core;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// ingest-core is a library module with no @SpringBootApplication of its own -
// this exists purely so @SpringBootTest has something to bootstrap a context
// from when testing this module's persistence/domain layer in isolation.
// MailboxPort is satisfied by NoOpMailboxPort's @ConditionalOnMissingBean
// fallback for tests that don't care about mailbox polling at all - tests
// that DO care provide their own fake via @Import instead.
@SpringBootApplication
@ConfigurationPropertiesScan
public class TestApplication {
}
