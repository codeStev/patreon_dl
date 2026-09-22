package de.codestev.patreoningest.core.fulfillment;

import de.codestev.patreoningest.core.acquisition.DownloadItem;
import de.codestev.patreoningest.core.acquisition.SourceType;

import java.nio.file.Path;

// Out-port, one per distribution mechanism. Deliberately stateless - no
// running service/daemon, no persisted state of its own (see the design
// doc's "Fulfillment deliberately owns no copy of DownloadItem").
public interface SourceDownloader {

    SourceType supports();

    DownloadResult fetch(DownloadItem item, Path targetDir, DownloadRuntimeOptions options)
            throws DownloadFailedException;
}
