package net.listingclock;
//

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.listingclock.core.Listing;
import net.listingclock.core.SaleTracker;
import net.listingclock.core.ServerGate;
import net.listingclock.core.TimeDisplay;
import net.listingclock.storage.LocalState;
import net.listingclock.storage.StateStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ListingClockClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ListingClock");
    private final SaleTracker tracker = new SaleTracker();
    private LocalState state = LocalState.empty();
    private StateStore store;
    private List<Listing> active = List.of();
    private UUID cachedOwner;
    private long lastRefreshNanos;
    private boolean wasActive;
    private boolean storageWarningShown;

    @Override public void onInitializeClient() {
        try {
            store = new StateStore(FabricLoader.getInstance().getConfigDir().resolve("listingclock"));
            state = store.load();
        } catch (IOException e) {
            LOGGER.error("ListingClock persistence unavailable; tracking disabled to protect existing data", e);
            closeStore();
        }

        ClientSendMessageEvents.COMMAND.register(command -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!enabled(client) || store == null) return;
            var stack = client.player.getMainHandStack();
            tracker.command(command, stack.isEmpty() ? null : new SaleTracker.ItemSnapshot(stack.getName().getString(),
                    Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount()), owner(client), System.nanoTime());
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message, overlay));
        // Observes a real server message even when a chat-filter mod hides it. These events are mutually exclusive.
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> receive(message, overlay));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetSession());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closeStore());
        registerCommands();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("listingclock", "listings"),
                (context, tickCounter) -> ListingClockHud.render(context, MinecraftClient.getInstance(),
                        enabled(MinecraftClient.getInstance()) && state.hudEnabled(), active));
    }

    private static UUID owner(MinecraftClient client) { return client.player.getUuid(); }

    static boolean enabled(MinecraftClient client) {
        return client.player != null && client.world != null && client.getNetworkHandler() != null
                && client.getCurrentServerEntry() != null
                && ServerGate.allows(client.getCurrentServerEntry().address, client.isInSingleplayer());
    }

    private void receive(Text text, boolean overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Auction feedback belongs in server chat, not action bars or player messages.
        if (overlay || !enabled(client) || store == null) return;
        tracker.message(text.getString(), owner(client), System.currentTimeMillis(), System.nanoTime()).ifPresent(listing -> {
            ArrayList<Listing> updated = new ArrayList<>(state.listings());
            updated.add(listing);
            if (commit(state.withListings(updated))) {
                feedback("Tracking #" + listing.shortId() + " · " + listing.quantity() + "× " + listing.itemName()
                        + " · $" + listing.price() + " · 24h remaining", Formatting.GRAY);
            }
        });
    }

    private void tick(MinecraftClient client) {
        boolean connected = enabled(client);
        if (!connected) {
            if (wasActive) resetSession();
            wasActive = false;
            return;
        }
        wasActive = true;
        if (store == null && !storageWarningShown) {
            feedback("Tracking disabled: save file could not be loaded or locked. Check latest.log; existing data was left untouched.", Formatting.RED);
            storageWarningShown = true;
        }
        long nanos = System.nanoTime();
        if (nanos - lastRefreshNanos >= 1_000_000_000L || !owner(client).equals(cachedOwner)) {
            if (tracker.expire(nanos) > 0) {
                feedback("Sale attempt timed out without a recognized success message; no timer added.", Formatting.DARK_GRAY);
            }
            refresh();
            lastRefreshNanos = nanos;
        }
    }

    private void refresh() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled(client)) { active = List.of(); cachedOwner = null; return; }
        cachedOwner = owner(client);
        long now = System.currentTimeMillis();
        active = state.listings().stream().filter(l -> l.owner().equals(cachedOwner) && l.active(now))
                .sorted(Comparator.comparingLong(Listing::expiresAtMillis).thenComparing(l -> l.id().toString())).toList();
    }

    private void resetSession() {
        tracker.reset();
        active = List.of();
        cachedOwner = null;
        lastRefreshNanos = 0;
    }

    private boolean commit(LocalState candidate) {
        if (store == null) { feedback("Save unavailable; no local changes made.", Formatting.RED); return false; }
        try {
            store.save(candidate);
            state = candidate;
            refresh();
            return true;
        } catch (IOException e) {
            LOGGER.error("Could not save ListingClock state", e);
            feedback("Could not save local tracker changes. Check disk permissions and latest.log.", Formatting.RED);
            return false;
        }
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("listingclock")
                        .then(literal("toggle").executes(context -> run(() -> {
                            if (!commit(state.toggleHud())) return 0;
                            feedback("HUD " + (state.hudEnabled() ? "shown." : "hidden."), Formatting.GRAY);
                            return 1;
                        })))
                        .then(literal("list").executes(context -> run(this::list)))
                        .then(literal("clear").then(argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    if (enabled(MinecraftClient.getInstance())) {
                                        refresh();
                                        for (Listing listing : active) {
                                            if (listing.id().toString().startsWith(builder.getRemainingLowerCase())) {
                                                builder.suggest(listing.id().toString());
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> run(() -> clear(StringArgumentType.getString(context, "id"))))))
                        .then(literal("clearall").executes(context -> run(this::clearAll)))
                        .then(literal("help").executes(context -> run(this::help)))
                        .executes(context -> run(this::help))));
    }

    private int run(java.util.function.IntSupplier action) {
        if (!enabled(MinecraftClient.getInstance())) {
            feedback("Only available on donutsmp.net.", Formatting.GRAY);
            return 0;
        }
        return action.getAsInt();
    }

    private int list() {
        refresh();
        feedback(active.size() + " active listing" + (active.size() == 1 ? "" : "s") + " (local tracker)", Formatting.GOLD);
        for (Listing listing : active) {
            long remaining = listing.remainingMillis(System.currentTimeMillis());
            Text line = Text.literal("#" + listing.id() + " · " + listing.quantity() + "× " + listing.itemName()
                            + " · $" + listing.price() + " · " + TimeDisplay.remaining(remaining))
                    .styled(style -> style.withColor(Formatting.GRAY)
                            .withClickEvent(new ClickEvent.CopyToClipboard(listing.id().toString())));
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(line);
        }
        return 1;
    }

    private int clear(String id) {
        String prefix = id.toLowerCase(Locale.ROOT);
        if (prefix.length() < 8 || !prefix.matches("[a-f0-9-]+")) {
            feedback("Use a full listing ID or a unique prefix of at least 8 characters. See /listingclock list.", Formatting.RED);
            return 0;
        }
        UUID player = owner(MinecraftClient.getInstance());
        List<Listing> found = state.listings().stream().filter(l -> l.owner().equals(player)
                && l.id().toString().startsWith(prefix)).toList();
        if (found.size() != 1) {
            feedback(found.isEmpty() ? "No matching listing ID." : "Ambiguous ID. Use the full ID.", Formatting.RED);
            return 0;
        }
        UUID target = found.getFirst().id();
        if (!commit(state.withListings(state.listings().stream().filter(l -> !l.id().equals(target)).toList()))) return 0;
        feedback("Removed #" + found.getFirst().shortId() + " locally. The actual auction is unchanged.", Formatting.GRAY);
        return 1;
    }

    private int clearAll() {
        UUID player = owner(MinecraftClient.getInstance());
        int count = (int) state.listings().stream().filter(l -> l.owner().equals(player)).count();
        if (!commit(state.withListings(state.listings().stream().filter(l -> !l.owner().equals(player)).toList()))) return 0;
        tracker.reset();
        feedback("Cleared " + count + " local listing(s). Actual auctions are unchanged.", Formatting.GRAY);
        return 1;
    }

    private int help() {
        feedback("/listingclock toggle — show/hide HUD", Formatting.GRAY);
        feedback("/listingclock list — listings and IDs (click an ID to copy)", Formatting.GRAY);
        feedback("/listingclock clear <id> — remove a local timer", Formatting.GRAY);
        feedback("/listingclock clearall — remove all your local timers", Formatting.GRAY);
        feedback("/listingclock help — show commands", Formatting.GRAY);
        return 1;
    }

    private static void feedback(String message, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) return;
        client.inGameHud.getChatHud().addMessage(Text.literal("[ListingClock] ").formatted(Formatting.GOLD)
                .append(Text.literal(message).formatted(color)));
    }

    private void closeStore() {
        if (store != null) {
            try { store.close(); } catch (IOException e) { LOGGER.warn("Could not close ListingClock save lock", e); }
            store = null;
        }
    }
}
