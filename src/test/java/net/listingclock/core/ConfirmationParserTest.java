package net.listingclock.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic fixtures plus concrete examples of the success-message format supplied by the user. */
class ConfirmationParserTest {
    private final ConfirmationParser parser = new ConfirmationParser();

    @ParameterizedTest @ValueSource(strings = {
            "You listed 64 Diamond for $ 100K",
            "You listed 1 Netherite Sword for $ 100K",
            "You listed 64 Diamond for $ 100K!",
            "You listed 64 Diamond for $   100K",
            "You listed 64 Diamond for $\u00a0100K"})
    void supportsUserReportedDonutFormat(String message) {
        var result = parser.success(message).orElseThrow();
        assertEquals(0, new java.math.BigDecimal("100000").compareTo(result.price()));
    }

    @ParameterizedTest @ValueSource(strings = {
            "You listed Diamond for $1,000!",
            "You have listed Diamond for $1000 on the auction house.",
            "[Auction House] You successfully listed Diamond for 1k!",
            "Auction » You have successfully listed Diamond for $1000!",
            "» Successfully listed Diamond for $1000.",
            "Your Diamond has been successfully listed for $1000.",
            "Your item has been listed on the auction house for $1000!",
            "§aʏᴏᴜ ʜᴀᴠᴇ ʟɪꜱᴛᴇᴅ Diamond ꜰᴏʀ $1000!"})
    void recognizesOnlyCompletedCreation(String message) { assertTrue(parser.success(message).isPresent(), message); }

    @ParameterizedTest @ValueSource(strings = {"You have listed your item on the auction house!", "Your item has been successfully listed in the auction house."})
    void allowsStrictUnpricedAcknowledgment(String message) {
        assertNull(parser.success(message).orElseThrow().price());
    }

    @ParameterizedTest @ValueSource(strings = {
            "<Alice> You listed Diamond for $1000!",
            "[VIP] Alice: You listed Diamond for $1000!",
            "Alice whispers: You have listed your item on the auction house!",
            "You sold Diamond for $1000!", "Your item was sold for $1000!",
            "You cannot list this item", "You have not listed Diamond for $1000!",
            "Click here to confirm listing Diamond for $1000", "Confirm: You listed Diamond for $1000!",
            "You listed Diamond for $1000! Click to cancel", "You listed Diamond for $0!",
            "You listed Diamond for $1000?", "Auction house opened", "Item listed", "You bought Diamond for $1000!"})
    void rejectsChatFailuresPromptsAndUnrelatedSales(String message) { assertTrue(parser.success(message).isEmpty(), message); }

    @Test void recognizesFailureAndIgnoresPlayerPrefix() {
        assertTrue(parser.failure("[AH] You have reached the auction limit!"));
        assertTrue(parser.failure("You cannot sell this item!"));
        assertFalse(parser.failure("<Alice> You cannot sell this item!"));
        assertFalse(parser.failure("You listed Diamond for $1000!"));
    }
}
