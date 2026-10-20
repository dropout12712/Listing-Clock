package net.listingclock.core;

import java.time.Duration;
import java.util.Locale;

public final class TimeDisplay {
    private TimeDisplay() { }

    public static String remaining(long millis) {
        if (millis <= 0) return "Expired";
        long minutes = millis / 60_000;
        if (minutes == 0) return "<1m remaining";
        long hours = minutes / 60;
        return hours > 0 ? String.format(Locale.ROOT, "%02dh %02dm remaining", hours, minutes % 60)
                : minutes + "m remaining";
    }

    public static int color(long millis) {
        if (millis <= Duration.ofMinutes(30).toMillis()) return 0xFFFF5555;
        if (millis <= Duration.ofHours(3).toMillis()) return 0xFFFFAA00;
        return 0xFFAAAAAA;
    }
}
