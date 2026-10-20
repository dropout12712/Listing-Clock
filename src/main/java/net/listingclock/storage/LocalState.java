package net.listingclock.storage;

import net.listingclock.core.Listing;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record LocalState(int schemaVersion, boolean hudEnabled, List<Listing> listings) {
    public LocalState {
        if (schemaVersion != 1 || listings == null) throw new IllegalArgumentException("Unsupported or incomplete state");
        listings = List.copyOf(listings);
        HashSet<UUID> ids = new HashSet<>();
        for (Listing listing : listings) {
            if (!ids.add(listing.id())) throw new IllegalArgumentException("Duplicate listing ID in save file");
        }
    }

    public static LocalState empty() { return new LocalState(1, true, List.of()); }
    public LocalState withListings(List<Listing> items) { return new LocalState(1, hudEnabled, items); }
    public LocalState toggleHud() { return new LocalState(1, !hudEnabled, listings); }
}
