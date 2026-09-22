package de.codestev.patreoningest.core.fulfillment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "patreon.fulfillment.rclone")
public record RcloneProperties(
        @DefaultValue("gdrive") String remoteName,
        @DefaultValue("rclone") String binaryPath,
        // Explicit rather than relying on rclone's own $HOME-based default
        // resolution - the container runs as a non-home-having --system
        // user, so "which HOME" isn't a question worth depending on.
        @DefaultValue("/config/rclone.conf") String configPath
) {
}
