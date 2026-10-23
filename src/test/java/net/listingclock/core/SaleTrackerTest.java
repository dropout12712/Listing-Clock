package net.listingclock.core;
//

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SaleTrackerTest {
    private final UUID owner = UUID.randomUUID();
    private final SaleTracker tracker = new SaleTracker();
    private final SaleTracker.ItemSnapshot diamond = new SaleTracker.ItemSnapshot("Diamond", "minecraft:diamond", 64);
    private final long now = 1_800_000_000_000L;
    private final long sent = 5_000_000_000L;

    private void send() { assertTrue(tracker.command("ah sell 1k", diamond, owner, sent)); }
    private java.util.Optional<Listing> receive(String message) { return tracker.message(message, owner, now, sent + 100); }

    @Test void userReportedQuantityItemAndSpacedPriceFormatCreatesListing() {
        assertTrue(tracker.command("ah sell 100k", diamond, owner, sent));
        Listing listing = receive("You listed 64 Diamond for $ 100K").orElseThrow();
        assertEquals(64, listing.quantity());
        assertEquals("Diamond", listing.itemName());
        assertEquals("100000", listing.price());
        assertEquals(now, listing.createdAtMillis());
        assertTrue(receive("You listed 64 Diamond for $ 100K").isEmpty());
    }

    @Test void userReportedFormatSupportsSingleMultiwordItem() {
        var sword = new SaleTracker.ItemSnapshot("Netherite Sword", "minecraft:netherite_sword", 1);
        assertTrue(tracker.command("ah sell 100000", sword, owner, sent));
        Listing listing = receive("You listed 1 Netherite Sword for $ 100K").orElseThrow();
        assertEquals("Netherite Sword", listing.itemName());
        assertEquals(1, listing.quantity());
        assertEquals("100000", listing.price());
    }

    @ParameterizedTest @ValueSource(strings = {
            "You listed 32 Diamond for $ 100K",
            "You listed 64 Diamond for $ 200K",
            "You listed 64 Emerald for $ 100K",
            "<Alice> You listed 64 Diamond for $ 100K",
            "You listed 64 Diamond for $ 100K?"})
    void userReportedFormatStillRejectsMismatchesAndSpoofedChat(String message) {
        assertTrue(tracker.command("ah sell 100k", diamond, owner, sent));
        assertTrue(receive(message).isEmpty());
    }

    @Test void doesNotTrackUntilSuccess() {
        send();
        assertEquals(1, tracker.pendingCount());
        assertTrue(receive("Auction House").isEmpty());
        Listing listing = receive("You listed Diamond for $1,000!").orElseThrow();
        assertEquals("Diamond", listing.itemName());
        assertEquals("minecraft:diamond", listing.itemType());
        assertEquals(64, listing.quantity());
        assertEquals("1000", listing.price());
        assertEquals(owner, listing.owner());
        assertEquals(now, listing.createdAtMillis());
        assertEquals(now + Listing.LIFETIME_MILLIS, listing.expiresAtMillis());
        assertEquals(0, tracker.pendingCount());
    }

    @Test void noPhantomListingWithoutCommandOrFromDuplicateFeedback() {
        assertTrue(receive("You listed Diamond for $1000!").isEmpty());
        send();
        assertTrue(receive("You listed Diamond for $1000!").isPresent());
        assertTrue(receive("You listed Diamond for $1000!").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"You cannot sell this item!", "[AH] You have reached the listing limit!", "Invalid price!", "Please wait before listing again."})
    void failuresDiscardPending(String failure) {
        send();
        assertTrue(receive(failure).isEmpty());
        assertEquals(0, tracker.pendingCount());
        assertTrue(receive("You listed Diamond for $1000!").isEmpty());
    }

    @Test void unrelatedPlayerChatNeitherConfirmsNorClears() {
        send();
        assertTrue(receive("<Alice> You listed Diamond for $1000!").isEmpty());
        assertTrue(receive("<Alice> You cannot sell this item!").isEmpty());
        assertEquals(1, tracker.pendingCount());
    }

    @Test void timeoutUsesMonotonicTimeAndNeverPersistsPendingAttempts() {
        send();
        assertEquals(0, tracker.expire(sent + SaleTracker.TIMEOUT_NANOS - 1));
        assertTrue(tracker.message("You listed Diamond for $1000!", owner, now,
                sent + SaleTracker.TIMEOUT_NANOS).isEmpty());
        assertEquals(0, tracker.pendingCount());
    }

    @Test void resetOnDisconnect() {
        send();
        tracker.reset();
        assertTrue(receive("You listed Diamond for $1000!").isEmpty());
    }

    @Test void priceAndItemMismatchesDoNotCreateTimers() {
        send();
        assertTrue(receive("You listed Diamond for $999!").isEmpty());
        send();
        assertTrue(receive("You listed Elytra for $1000!").isEmpty());
        send();
        assertTrue(receive("You listed 32x Diamond for $1000!").isEmpty());
        send();
        assertTrue(receive("You listed 64x Diamond for $1000!").isPresent());
    }

    @Test void preservesCustomNameSnapshot() {
        tracker.command("ah sell 0.1m", new SaleTracker.ItemSnapshot("Bob's Blade", "minecraft:diamond_sword", 1), owner, sent);
        Listing result = receive("You listed Bob’s Blade for $100,000!").orElseThrow();
        assertEquals("Bob's Blade", result.itemName());
        assertEquals("100000", result.price());
    }

    @Test void genericConfirmationOnlyConfirmsOnePendingAttempt() {
        send();
        assertTrue(receive("Your item has been listed on the auction house!").isPresent());
        send();
        tracker.command("ah sell 2k", diamond, owner, sent + 1);
        assertTrue(receive("Your item has been listed on the auction house!").isEmpty());
        assertEquals(0, tracker.pendingCount());
    }

    @Test void matchesOverlappingAttemptsByExactPrice() {
        send();
        tracker.command("ah sell 2k", diamond, owner, sent + 1);
        Listing second = receive("You listed Diamond for $2000!").orElseThrow();
        Listing first = receive("You listed Diamond for $1000!").orElseThrow();
        assertEquals("2000", second.price());
        assertEquals("1000", first.price());
        assertNotEquals(first.id(), second.id());
    }

    @Test void refusesAmbiguousDuplicateAttempts() {
        send();
        tracker.command("ah sell 1k", diamond, owner, sent + 1);
        assertTrue(receive("You listed Diamond for $1000!").isEmpty());
    }

    @Test void duplicateItemsHaveDifferentIdsWhenIndividuallyConfirmed() {
        send();
        Listing first = receive("You listed Diamond for $1000!").orElseThrow();
        send();
        Listing second = receive("You listed Diamond for $1000!").orElseThrow();
        assertNotEquals(first.id(), second.id());
    }

    @Test void rejectsAcknowledgmentForAnotherAccount() {
        send();
        assertTrue(tracker.message("You listed Diamond for $1000!", UUID.randomUUID(), now, sent + 100).isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"ah buy 1k", "say /ah sell 1k", "ah sell", "ah sell -1", "ah sell 0", "ah sell 1k 64", "ah sell NaN", "ah sell 1e6"})
    void rejectsUnsupportedCommands(String command) { assertFalse(tracker.command(command, diamond, owner, sent)); }

    @Test void emptyHandAttemptInvalidatesEarlierSnapshot() {
        send();
        assertFalse(tracker.command("ah sell 1k", null, owner, sent + 1));
        assertTrue(receive("You listed Diamond for $1000!").isEmpty());
    }
}
