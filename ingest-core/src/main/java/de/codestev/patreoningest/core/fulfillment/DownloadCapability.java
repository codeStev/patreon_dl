package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;

// Whether the app can fetch a source type's bytes at all. Some items only
// exist to be claimed or retrieved by hand - Wicked's Gumroad lines (their
// files come from the Drive term folder instead) and Bulkamancer's
// MyMiniFactory entries have no SourceDownloader. Dispatching those would
// leave them PENDING and re-dispatched every queue tick forever, so the
// queue, the manual trigger and the admin UI all check this first.
@Component
public class DownloadCapability {

    private final List<SourceDownloader> downloaders;

    public DownloadCapability(List<SourceDownloader> downloaders) {
        this.downloaders = downloaders;
    }

    public boolean canDownload(SourceType sourceType) {
        return downloaders.stream().anyMatch(d -> d.supports() == sourceType);
    }
}
