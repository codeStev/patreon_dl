package de.codestev.patreoningest.core.acquisition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SingleModelFolderTest {

    private static DriveEntry dir(String name) {
        return new DriveEntry(name + "-id", name, true);
    }

    private static DriveEntry file(String name) {
        return new DriveEntry(name + "-id", name, false);
    }

    @Test
    void theFoldersOfARealNomnomLinkAreOneModel() {
        // https://drive.google.com/drive/folders/1_eSq8xMKhY4SH5Fg1s7LGIUTd_idlmmF
        assertThat(SingleModelFolder.matches(List.of(dir("STL"), dir("Render Images"), dir("Presupports")))).isTrue();
    }

    @Test
    void pluralsAndSpellingVariantsCount() {
        for (String name : List.of("Presupport", "Presupports", "PreSupported", "Pre-Supported", "pre_supports", "STLs",
                "STL Files", "No Supports", "Non Supported", "Renders", "Images", "Lychee Files", "Chitubox", "75mm", "32mm Scale",
                "Uncut", "Textures", "Previews", "Parts")) {
            assertThat(SingleModelFolder.isOrganizational(name)).as(name).isTrue();
        }
    }

    @Test
    void realNamesAreNotOrganizational() {
        for (String name : List.of("Cool Dragon", "Chibi He-Man", "Sousou no Frieren", "Wolverine", "Base", "Bonus", "", "  -  ")) {
            assertThat(SingleModelFolder.isOrganizational(name)).as(name).isFalse();
        }
    }

    @Test
    void aCoverImageOrReadmeNextToTheSubfoldersDoesNotBreakIt() {
        assertThat(SingleModelFolder.matches(List.of(dir("STL"), dir("Render Images"), file("cover.jpg"), file("README.txt")))).isTrue();
    }

    @Test
    void anArchiveOrPrintFileNextToThemMeansItIsNotOneModel() {
        assertThat(SingleModelFolder.matches(List.of(dir("STL"), file("Other Model.zip")))).isFalse();
        assertThat(SingleModelFolder.matches(List.of(dir("STL"), file("head.stl")))).isFalse();
    }

    @Test
    void oneRealNameAmongTheSubfoldersMeansAFolderOfModels() {
        assertThat(SingleModelFolder.matches(List.of(dir("STL"), dir("Cool Dragon")))).isFalse();
    }

    @Test
    void noFoldersMeansNothingToDecide() {
        assertThat(SingleModelFolder.matches(List.of())).isFalse();
        assertThat(SingleModelFolder.matches(List.of(file("cover.jpg")))).isFalse();
    }
}
