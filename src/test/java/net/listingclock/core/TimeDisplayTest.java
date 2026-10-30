package net.listingclock.core;
//

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(UrgencyPalette.class)
class TimeDisplayTest {
    @ParameterizedTest @CsvSource({"0,Expired", "-1,Expired", "1,<1m remaining", "59999,<1m remaining", "60000,1m remaining", "1680000,28m remaining", "11640000,03h 14m remaining", "66720000,18h 32m remaining", "86400000,24h 00m remaining"})
    void formats(long millis, String expected) { assertEquals(expected, TimeDisplay.remaining(millis)); }

    @Test void expirationAndUrgencyBoundaries() {
        Listing listing = new Listing(UUID.randomUUID(), UUID.randomUUID(), "Elytra", "minecraft:elytra", 1, "100", 1000);
        assertTrue(listing.active(1000 + Listing.LIFETIME_MILLIS - 1));
        assertFalse(listing.active(1000 + Listing.LIFETIME_MILLIS));
        assertEquals(0, listing.remainingMillis(2000 + Listing.LIFETIME_MILLIS));
        assertEquals(0xFFFF5555, TimeDisplay.color(Duration.ofMinutes(30).toMillis()));
        assertEquals(0xFFFFAA00, TimeDisplay.color(Duration.ofMinutes(30).toMillis() + 1));
        assertEquals(0xFFFFAA00, TimeDisplay.color(Duration.ofHours(3).toMillis()));
        assertEquals(0xFFAAAAAA, TimeDisplay.color(Duration.ofHours(3).toMillis() + 1));
    }
}
