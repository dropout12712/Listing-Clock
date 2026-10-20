package net.listingclock.core;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ServerGate {
    private static final Pattern ADDRESS = Pattern.compile(
            "^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)*donutsmp\\.net\\.?(?::([0-9]{1,5}))?$");

    private ServerGate() { }

    /** Check the entered multiplayer hostname, never a substring or a DNS reverse lookup. */
    public static boolean allows(String address, boolean singleplayer) {
        if (singleplayer || address == null) return false;
        var match = ADDRESS.matcher(address.trim().toLowerCase(Locale.ROOT));
        if (!match.matches()) return false;
        return match.group(1) == null || (Integer.parseInt(match.group(1)) >= 1
                && Integer.parseInt(match.group(1)) <= 65535);
    }
}
