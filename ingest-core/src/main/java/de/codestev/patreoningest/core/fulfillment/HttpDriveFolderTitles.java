package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DriveFolderTitles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a shared folder's name from the title of its public share page
 * ("Chibi He-Man - Google Drive"): the page of a link-shared folder needs no
 * login. Anything unexpected (a private folder's sign-in page, an error
 * page, a changed layout, no network) yields empty, never an exception.
 */
@Component
public class HttpDriveFolderTitles implements DriveFolderTitles {

    private static final Logger log = LoggerFactory.getLogger(HttpDriveFolderTitles.class);

    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // Google separates the name from its suffix with an en dash (or a hyphen).
    private static final Pattern DRIVE_SUFFIX = Pattern.compile("\\s+[\u2013\u2014-]\\s+Google Drive\\s*$");
    private static final int MAX_TITLE_LENGTH = 100;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Optional<String> titleOf(String folderId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://drive.google.com/drive/folders/" + folderId))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return parseTitle(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Could not read the name of Drive folder {}: {}", folderId, e.toString());
            return Optional.empty();
        }
    }

    /** The folder name in an HTML page, if the title has the "<name> - Google Drive" shape. */
    static Optional<String> parseTitle(String html) {
        Matcher m = TITLE.matcher(html);
        if (!m.find()) {
            return Optional.empty();
        }
        String title = unescape(m.group(1)).trim();
        Matcher suffix = DRIVE_SUFFIX.matcher(title);
        if (!suffix.find()) {
            return Optional.empty(); // "Sign in", an error page, ...
        }
        String name = title.substring(0, suffix.start()).trim();
        if (name.isEmpty() || name.length() > MAX_TITLE_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    private static String unescape(String s) {
        String out = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
        Matcher n = Pattern.compile("&#(\\d+);").matcher(out);
        StringBuilder sb = new StringBuilder();
        while (n.find()) {
            n.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(Integer.parseInt(n.group(1))))));
        }
        n.appendTail(sb);
        return sb.toString();
    }
}
