package de.codestev.patreoningest.core.acquisition;

// One top-level entry in a Drive folder listing. Per the user's confirmed
// mental model, each top-level entry (almost always a folder) IS one model
// - no recursion into subfolders.
public record DriveEntry(String id, String name, boolean isDirectory) {
}
