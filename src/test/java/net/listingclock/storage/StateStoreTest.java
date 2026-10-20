package net.listingclock.storage;

import net.listingclock.core.Listing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StateStoreTest {
    @TempDir Path directory;
    private final Listing sample = new Listing(UUID.randomUUID(), UUID.randomUUID(), "Élytra <Flight>", "minecraft:elytra", 1, "123456.78", 1_800_000_000_000L);

    @Test void defaultsAreEmptyWithVisibleHud() throws Exception {
        try (var store = new StateStore(directory)) {
            assertEquals(LocalState.empty(), store.load());
            assertTrue(store.load().hudEnabled());
        }
    }

    @Test void restartPreservesExactAbsoluteTimeAndAccountAndHud() throws Exception {
        LocalState state = new LocalState(1, false, List.of(sample));
        try (var store = new StateStore(directory)) { store.save(state); }
        try (var store = new StateStore(directory)) {
            assertEquals(state, store.load());
            Listing restored = store.load().listings().getFirst();
            assertEquals(18 * 3_600_000L, restored.remainingMillis(sample.createdAtMillis() + 6 * 3_600_000L));
            assertFalse(restored.active(sample.createdAtMillis() + Listing.LIFETIME_MILLIS));
        }
    }

    @Test void keepsPreviousSaveAsBackupAndLeavesNoTemporaryFiles() throws Exception {
        try (var store = new StateStore(directory)) {
            store.save(LocalState.empty());
            String previous = Files.readString(directory.resolve("listings.json"));
            store.save(new LocalState(1, true, List.of(sample)));
            assertEquals(previous, Files.readString(directory.resolve("listings.json.bak")));
            assertEquals(List.of(sample), store.load().listings());
            try (var paths = Files.list(directory)) { assertFalse(paths.anyMatch(p -> p.toString().endsWith(".tmp"))); }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"", "{", "null", "[]", "{}", "{\"schemaVersion\":2,\"hudEnabled\":true,\"listings\":[]}", "{\"schemaVersion\":1,\"listings\":[]}", "{\"schemaVersion\":1,\"hudEnabled\":\"false\",\"listings\":[]}", "{\"schemaVersion\":1,\"hudEnabled\":true,\"listings\":[null]}", "{\"schemaVersion\":1,\"hudEnabled\":true,\"listings\":[{}]}", "{\"schemaVersion\":1,\"hudEnabled\":true,\"listings\":[]} trailing"})
    void malformedStateIsNotOverwritten(String raw) throws Exception {
        Files.writeString(directory.resolve("listings.json"), raw);
        try (var store = new StateStore(directory)) { assertThrows(IOException.class, store::load); }
        assertEquals(raw, Files.readString(directory.resolve("listings.json")));
    }

    @Test void secondInstanceCannotOverwriteFirstAndLockReleases() throws Exception {
        try (var first = new StateStore(directory)) {
            assertThrows(IOException.class, () -> new StateStore(directory));
            first.save(LocalState.empty());
        }
        try (var next = new StateStore(directory)) { assertEquals(LocalState.empty(), next.load()); }
    }

    @Test void duplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LocalState(1, true, List.of(sample, sample)));
    }

    @Test void immutableStateCannotAccidentallyChangeBeforeSave() {
        var list = new java.util.ArrayList<>(List.of(sample));
        LocalState state = new LocalState(1, true, list);
        list.clear();
        assertEquals(1, state.listings().size());
        assertThrows(UnsupportedOperationException.class, () -> state.listings().clear());
    }
}
