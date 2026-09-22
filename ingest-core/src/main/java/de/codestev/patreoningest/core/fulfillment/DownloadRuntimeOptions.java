package de.codestev.patreoningest.core.fulfillment;

// Passed into a SourceDownloader per invocation rather than having it read
// AppSettings itself - keeps the adapter stateless/testable, and settings
// changes apply from the very next dispatched item without any caching
// concerns inside the adapter.
public record DownloadRuntimeOptions(Integer bandwidthLimitKbps, boolean ioNice) {
}
