package de.codestev.patreoningest.core.acquisition;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a Drive folder is ONE model's own folder - its entries
 * being the model's organizational subfolders ("STL", "Render Images",
 * "Presupports") rather than separate models. Such a folder is registered
 * as a single item; enumerating it would turn each subfolder into a
 * "model" of its own and merge them with other models in the library.
 *
 * <p>Entries are judged by their words, not exact names, so plurals and
 * spelling variants ("Presupport" / "Presupports" / "Pre-Supported",
 * "STL Files") count. A stray cover image or readme next to the
 * subfolders doesn't break it; any other file (an archive, a print file)
 * or any folder with a real name does - then the folder is enumerated as
 * before.
 */
final class SingleModelFolder {

    private static final Set<String> ORGANIZATIONAL_WORDS = Set.of(
            "stl", "stls", "render", "renders", "image", "images",
            "presupport", "presupports", "presupported", "pre",
            "support", "supports", "supported", "unsupported", "non", "no", "uncut",
            "texture", "textures", "preview", "previews", "part", "parts",
            "chitubox", "lys", "lychee", "file", "files", "sliced", "print", "prints",
            "scale", "split"
    );

    private static final Pattern SIZE_IN_MM = Pattern.compile("\\d+mm");

    /** Files that come along with a model without being one: covers, readmes. */
    private static final Set<String> INCIDENTAL_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "pdf", "txt", "md", "url", "html", "ini", "db"
    );

    private SingleModelFolder() {
    }

    static boolean matches(List<DriveEntry> entries) {
        boolean anyFolder = false;
        for (DriveEntry entry : entries) {
            if (!entry.isDirectory() && isIncidentalFile(entry.name())) {
                continue;
            }
            if (!isOrganizational(entry.name())) {
                return false;
            }
            anyFolder |= entry.isDirectory();
        }
        return anyFolder;
    }

    static boolean isOrganizational(String name) {
        String[] words = name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        boolean any = false;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!ORGANIZATIONAL_WORDS.contains(word) && !SIZE_IN_MM.matcher(word).matches()) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private static boolean isIncidentalFile(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && INCIDENTAL_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
