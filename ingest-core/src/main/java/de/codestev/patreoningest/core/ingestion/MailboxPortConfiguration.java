package de.codestev.patreoningest.core.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Not configuring a mailbox (patreon.imap.host unset) is a legitimate,
// common deployment state - e.g. before the operator finishes setup - and
// must not crash the app just because PollMailboxUseCase needs a
// MailboxPort. This must be a @Bean factory method, not a @Component
// directly implementing MailboxPort - @ConditionalOnMissingBean on a class
// that itself implements the target interface always finds itself already
// registered and skips, which is self-defeating.
@Configuration
class MailboxPortConfiguration {

    @Bean
    @ConditionalOnMissingBean(MailboxPort.class)
    MailboxPort noOpMailboxPort() {
        return List::of;
    }
}
