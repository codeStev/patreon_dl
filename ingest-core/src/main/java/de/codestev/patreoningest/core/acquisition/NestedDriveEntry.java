package de.codestev.patreoningest.core.acquisition;

// One entry from a two-level folder listing. parentName is the top-level
// folder the entry sits in, or null for a top-level entry itself.
public record NestedDriveEntry(String parentName, DriveEntry entry) {

    public boolean isTopLevel() {
        return parentName == null;
    }
}
