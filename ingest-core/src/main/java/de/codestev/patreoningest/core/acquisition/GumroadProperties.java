package de.codestev.patreoningest.core.acquisition;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "patreon.acquisition.gumroad")
public record GumroadProperties(
        // The email the free checkout is placed under - claimed products
        // land in the Gumroad library of the account with this address.
        // Leaving it blank disables GumroadClaimAdapter entirely.
        String email,
        // Only worth turning off for local debugging, to watch the flow.
        @DefaultValue("true") boolean headless
) {
}
