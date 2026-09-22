package de.codestev.patreoningest.core.fulfillment;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class AppSettingsTest {

    @Test
    void allowsDownloadsAtAnyTimeWhenNoWindowIsConfigured() {
        AppSettings settings = new AppSettings(1L, 1, true);

        assertThat(settings.allowsDownloadsAt(LocalTime.of(3, 0))).isTrue();
        assertThat(settings.allowsDownloadsAt(LocalTime.of(15, 0))).isTrue();
    }

    @Test
    void respectsASimpleDaytimeWindow() {
        AppSettings settings = new AppSettings(1L, 1, true);
        settings.update(1, null, true, LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThat(settings.allowsDownloadsAt(LocalTime.of(8, 59))).isFalse();
        assertThat(settings.allowsDownloadsAt(LocalTime.of(9, 0))).isTrue();
        assertThat(settings.allowsDownloadsAt(LocalTime.of(16, 59))).isTrue();
        assertThat(settings.allowsDownloadsAt(LocalTime.of(17, 0))).isFalse();
    }

    @Test
    void handlesAWindowThatCrossesMidnight() {
        AppSettings settings = new AppSettings(1L, 1, true);
        settings.update(1, null, true, LocalTime.of(22, 0), LocalTime.of(6, 0));

        assertThat(settings.allowsDownloadsAt(LocalTime.of(23, 0))).isTrue();
        assertThat(settings.allowsDownloadsAt(LocalTime.of(3, 0))).isTrue();
        assertThat(settings.allowsDownloadsAt(LocalTime.of(12, 0))).isFalse();
        assertThat(settings.allowsDownloadsAt(LocalTime.of(21, 59))).isFalse();
    }
}
