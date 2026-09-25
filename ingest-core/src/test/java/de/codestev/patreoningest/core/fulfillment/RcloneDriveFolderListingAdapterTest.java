package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DriveEntry;
import de.codestev.patreoningest.core.acquisition.NestedDriveEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RcloneDriveFolderListingAdapterTest {

    // Shape of `rclone lsjson` output against Drive (fields trimmed to the
    // ones rclone always emits).
    private static final String TOP_LEVEL = """
            [
            {"Path":"Titan Forge","Name":"Titan Forge","Size":-1,"MimeType":"inode/directory","ModTime":"2026-09-01T10:00:00.000Z","IsDir":true,"ID":"folder-titan"},
            {"Path":"Readme.pdf","Name":"Readme.pdf","Size":1024,"MimeType":"application/pdf","ModTime":"2026-09-01T10:00:00.000Z","IsDir":false,"ID":"file-readme"}
            ]
            """;

    private static final String TWO_LEVELS = """
            [
            {"Path":"Titan Forge","Name":"Titan Forge","Size":-1,"MimeType":"inode/directory","ModTime":"2026-09-01T10:00:00.000Z","IsDir":true,"ID":"folder-titan"},
            {"Path":"Titan Forge/2025-01 Release","Name":"2025-01 Release","Size":-1,"MimeType":"inode/directory","ModTime":"2026-09-01T10:00:00.000Z","IsDir":true,"ID":"collection-jan"},
            {"Path":"Titan Forge/Bonus.zip","Name":"Bonus.zip","Size":2048,"MimeType":"application/zip","ModTime":"2026-09-01T10:00:00.000Z","IsDir":false,"ID":"file-bonus"},
            {"Path":"Scale 1／4 Pack","Name":"Scale 1／4 Pack","Size":-1,"MimeType":"inode/directory","ModTime":"2026-09-01T10:00:00.000Z","IsDir":true,"ID":"folder-scale"},
            {"Path":"Scale 1／4 Pack/March","Name":"March","Size":-1,"MimeType":"inode/directory","ModTime":"2026-09-01T10:00:00.000Z","IsDir":true,"ID":"collection-march"}
            ]
            """;

    @Test
    void parsesTopLevelEntries() {
        assertThat(RcloneDriveFolderListingAdapter.parseEntries(TOP_LEVEL)).containsExactly(
                new DriveEntry("folder-titan", "Titan Forge", true),
                new DriveEntry("file-readme", "Readme.pdf", false));
    }

    @Test
    void parsesTwoLevelsWithTheParentTakenFromThePath() {
        assertThat(RcloneDriveFolderListingAdapter.parseNestedEntries(TWO_LEVELS)).containsExactly(
                new NestedDriveEntry(null, new DriveEntry("folder-titan", "Titan Forge", true)),
                new NestedDriveEntry("Titan Forge", new DriveEntry("collection-jan", "2025-01 Release", true)),
                new NestedDriveEntry("Titan Forge", new DriveEntry("file-bonus", "Bonus.zip", false)),
                new NestedDriveEntry(null, new DriveEntry("folder-scale", "Scale 1／4 Pack", true)),
                new NestedDriveEntry("Scale 1／4 Pack", new DriveEntry("collection-march", "March", true)));
    }
}
