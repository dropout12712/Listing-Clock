package net.listingclock;
//

import net.listingclock.core.Listing;
import net.listingclock.core.TimeDisplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Vanilla font, chat-like translucent background, left edge to avoid the server scoreboard. */
final class ListingClockHud {
    private ListingClockHud() { }

    static void render(DrawContext context, MinecraftClient client, boolean enabled, List<Listing> cached) {
        if (!enabled || client.options.hudHidden || client.currentScreen != null || cached.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Listing> listings = cached.stream().filter(l -> l.active(now)).toList();
        if (listings.isEmpty()) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int width = Math.min(174, screenWidth / 3);
        if (width < 90 || screenHeight < 110) return;
        int maxRows = Math.max(1, Math.min(8, (screenHeight - 110) / 25));
        int shown = Math.min(listings.size(), maxRows);
        int overflow = listings.size() - shown;
        int height = 40 + shown * 25 + (overflow > 0 ? 12 : 0);
        int x = 6;
        int y = Math.max(28, (screenHeight - height) / 2);
        var font = client.textRenderer;

        context.fill(x, y, x + width, y + height, 0x99000000);
        context.drawTextWithShadow(font, "ListingClock", x + 6, y + 6, 0xFFFFAA00);
        context.fill(x + 6, y + 19, x + width - 6, y + 20, 0x66AAAAAA);

        int rowY = y + 25;
        for (int i = 0; i < shown; i++) {
            Listing listing = listings.get(i);
            String title = (i + 1) + ". " + (listing.quantity() > 1 ? listing.quantity() + "× " : "") + listing.itemName();
            context.drawTextWithShadow(font, fit(client, title, width - 12), x + 6, rowY, 0xFFFFFFFF);
            long remaining = listing.remainingMillis(now);
            context.drawTextWithShadow(font, fit(client, TimeDisplay.remaining(remaining), width - 20), x + 14, rowY + 11, TimeDisplay.color(remaining));
            rowY += 25;
        }
        if (overflow > 0) {
            context.drawTextWithShadow(font, fit(client, "+" + overflow + " more · /listingclock list", width - 12), x + 6, rowY - 1, 0xFF888888);
            rowY += 12;
        }
        context.fill(x + 6, rowY - 1, x + width - 6, rowY, 0x66AAAAAA);
        context.drawTextWithShadow(font, fit(client, listings.size() + " Active Listing" + (listings.size() == 1 ? "" : "s"), width - 12),
                x + 6, rowY + 4, 0xFFAAAAAA);
    }

    private static String fit(MinecraftClient client, String text, int width) {
        if (client.textRenderer.getWidth(text) <= width) return text;
        return client.textRenderer.trimToWidth(text, width - client.textRenderer.getWidth("...")) + "...";
    }
}
