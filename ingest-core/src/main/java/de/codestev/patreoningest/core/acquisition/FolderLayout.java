package de.codestev.patreoningest.core.acquisition;

// How FolderSyncJob turns a Drive folder source into download items.
public enum FolderLayout {
    // Each top-level entry is one model - the email-parsed creator folders.
    MODELS,
    // Top-level folders group collections (usually one folder per creator,
    // holding one folder per monthly release) - each collection one level
    // down is one item. For persistent links whose content keeps growing.
    COLLECTIONS
}
