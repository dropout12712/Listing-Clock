package net.listingclock.core;

import java.time.Duration;
import java.util.UUID;

public record Listing(UUID id, UUID owner, String itemName, String itemType, int quantity,
                      String price, long createdAtMillis) {
    public static final long LIFETIME_MILLIS = Duration.ofHours(24).toMillis();

    public Listing {
        if (id == null || owner == null || itemName == null || itemName.isBlank() || itemName.length() > 512
                || itemType == null || !itemType.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || quantity < 1 || quantity > 999999 || Price.parse(price).isEmpty()
                || createdAtMillis < 1 || createdAtMillis > Long.MAX_VALUE - LIFETIME_MILLIS) {
            throw new IllegalArgumentException("Invalid listing data");
        }
    }

    public long expiresAtMillis() { return createdAtMillis + LIFETIME_MILLIS; }
    public long remainingMillis(long now) { return Math.max(0, expiresAtMillis() - now); }
    public boolean active(long now) { return now < expiresAtMillis(); }
    public String shortId() { return id.toString().substring(0, 8); }
}
