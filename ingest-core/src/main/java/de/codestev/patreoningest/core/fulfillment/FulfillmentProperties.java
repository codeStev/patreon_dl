package de.codestev.patreoningest.core.fulfillment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "patreon.fulfillment")
public record FulfillmentProperties(
        @DefaultValue("/downloads") String downloadRoot
) {
}
