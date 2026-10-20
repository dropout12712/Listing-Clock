package net.listingclock.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Atomic UTF-8 saves, previous-good backup, strict loading, and a lifetime single-writer lock. */
public final class StateStore implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long MAX_BYTES = 16 * 1024 * 1024;
    private final Path file;
    private final FileChannel lockChannel;
    private final FileLock lock;

    public StateStore(Path directory) throws IOException {
        Files.createDirectories(directory);
        file = directory.resolve("listings.json");
        lockChannel = FileChannel.open(directory.resolve("listings.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
            if (lock == null) throw new IOException("Another Minecraft instance is using ListingClock's save file");
        } catch (Exception e) {
            lockChannel.close();
            throw new IOException("Could not lock ListingClock's save file", e);
        }
    }

    public LocalState load() throws IOException {
        if (!Files.exists(file)) return LocalState.empty();
        if (Files.size(file) > MAX_BYTES) throw new IOException("ListingClock save exceeds 16 MiB; original left untouched");
        try (var reader = new com.google.gson.stream.JsonReader(new StringReader(Files.readString(file, StandardCharsets.UTF_8)))) {
            reader.setStrictness(Strictness.STRICT);
            var json = JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON");
            if (!json.isJsonObject()) throw new IllegalArgumentException("Expected object");
            var object = json.getAsJsonObject();
            if (!object.has("schemaVersion") || !object.has("hudEnabled") || !object.has("listings")
                    || !object.get("hudEnabled").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("hudEnabled").isBoolean()) {
                throw new IllegalArgumentException("Missing or invalid required fields");
            }
            LocalState result = GSON.fromJson(object, LocalState.class);
            if (result == null) throw new IllegalArgumentException("Empty state");
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Cannot read ListingClock save; original left untouched: " + file, e);
        }
    }

    public void save(LocalState state) throws IOException {
        byte[] bytes = (GSON.toJson(state) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("ListingClock save exceeds 16 MiB");
        Path temporary = Files.createTempFile(file.getParent(), "listings-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling("listings.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override public void close() throws IOException {
        try { lock.release(); } finally { lockChannel.close(); }
    }
}
