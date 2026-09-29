package de.codestev.patreoningest.core.fulfillment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpDriveFolderTitlesTest {

    private static String page(String title) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + title + "</title></head><body></body></html>";
    }

    @Test
    void theNameIsTheTitleWithoutTheDriveSuffix() {
        assertThat(HttpDriveFolderTitles.parseTitle(page("Super Mario Galaxy Movie – Google Drive"))).contains("Super Mario Galaxy Movie");
        assertThat(HttpDriveFolderTitles.parseTitle(page("Skeletor - He-man and the Masters of the Universe – Google Drive")))
                .contains("Skeletor - He-man and the Masters of the Universe");
        assertThat(HttpDriveFolderTitles.parseTitle(page("Chibi He-Man - Google Drive"))).contains("Chibi He-Man");
    }

    @Test
    void htmlEntitiesInTheNameAreDecoded() {
        assertThat(HttpDriveFolderTitles.parseTitle(page("Rem &amp; Ram &#8211; Re:Zero – Google Drive"))).contains("Rem & Ram – Re:Zero");
        assertThat(HttpDriveFolderTitles.parseTitle(page("Assassin&#39;s Creed – Google Drive"))).contains("Assassin's Creed");
    }

    @Test
    void pagesThatAreNoFolderTitleGiveNothing() {
        assertThat(HttpDriveFolderTitles.parseTitle(page("Sign in - Google Accounts"))).isEmpty();
        assertThat(HttpDriveFolderTitles.parseTitle(page("Google Drive"))).isEmpty();
        assertThat(HttpDriveFolderTitles.parseTitle(page(" – Google Drive"))).isEmpty();
        assertThat(HttpDriveFolderTitles.parseTitle(page("x".repeat(150) + " – Google Drive"))).isEmpty();
        assertThat(HttpDriveFolderTitles.parseTitle("<html>no title here</html>")).isEmpty();
    }
}
