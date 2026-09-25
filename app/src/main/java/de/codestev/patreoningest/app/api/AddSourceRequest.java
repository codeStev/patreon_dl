package de.codestev.patreoningest.app.api;

import de.codestev.patreoningest.core.acquisition.FolderLayout;

// layout is optional - null means MODELS, the same as email-parsed sources.
public record AddSourceRequest(String name, String url, FolderLayout layout) {
}
