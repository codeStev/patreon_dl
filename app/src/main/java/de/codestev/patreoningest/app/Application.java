package de.codestev.patreoningest.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Application lives in .app, a *sibling* of .core.acquisition/.ingestion/
// .fulfillment, not their parent - so @EntityScan/@EnableJpaRepositories
// must be explicit; the default auto-configuration package (this class's
// own package) would never reach the repositories/entities in .core.
@SpringBootApplication(scanBasePackages = "de.codestev.patreoningest")
@EntityScan("de.codestev.patreoningest.core")
@EnableJpaRepositories("de.codestev.patreoningest.core")
@ConfigurationPropertiesScan("de.codestev.patreoningest")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
