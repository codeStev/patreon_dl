package de.codestev.patreoningest.core.acquisition;

import de.codestev.patreoningest.core.TestApplication;
import de.codestev.patreoningest.core.ingestion.CreatorMessageParser;
import de.codestev.patreoningest.core.ingestion.ParsedItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@Import(ProviderSettingsSeederTest.FakeProviderConfig.class)
@Transactional
class ProviderSettingsSeederTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ProviderSettingsSeeder seeder;

    @Autowired
    private ProviderSettingsRepository providerSettingsRepository;

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean
        CreatorMessageParser fakeParser() {
            return new CreatorMessageParser() {
                @Override
                public String providerId() {
                    return "fake-provider";
                }

                @Override
                public boolean supports(String fromAddress, String subject) {
                    return false;
                }

                @Override
                public List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt) {
                    return List.of();
                }
            };
        }
    }

    @Test
    void seedsAMissingRowWithDefaultManualPolicy() {
        seeder.run(null);

        ProviderSettings settings = providerSettingsRepository.findById("fake-provider").orElseThrow();
        assertThat(settings.getDownloadPolicy()).isEqualTo(DownloadPolicy.MANUAL);
    }

    @Test
    void neverOverwritesAnExistingRow() {
        providerSettingsRepository.save(new ProviderSettings("fake-provider", DownloadPolicy.EAGER));

        seeder.run(null);

        ProviderSettings settings = providerSettingsRepository.findById("fake-provider").orElseThrow();
        assertThat(settings.getDownloadPolicy()).isEqualTo(DownloadPolicy.EAGER);
    }
}
