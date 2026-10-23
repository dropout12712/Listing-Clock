package net.listingclock.core;
//

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Price {
    public static final String TOKEN = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,8})?[kmbt]?";
    private static final Pattern VALID = Pattern.compile("^" + TOKEN + "$", Pattern.CASE_INSENSITIVE);

    private Price() { }

    public static Optional<BigDecimal> parse(String input) {
        if (input == null || input.length() > 40) return Optional.empty();
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("$")) value = value.substring(1).trim();
        if (!VALID.matcher(value).matches()) return Optional.empty();
        char suffix = value.charAt(value.length() - 1);
        int power = switch (suffix) { case 'k' -> 3; case 'm' -> 6; case 'b' -> 9; case 't' -> 12; default -> 0; };
        if (power > 0) value = value.substring(0, value.length() - 1);
        BigDecimal result = new BigDecimal(value.replace(",", "")).scaleByPowerOfTen(power).stripTrailingZeros();
        return result.signum() > 0 && result.precision() <= 24 ? Optional.of(result) : Optional.empty();
    }
}
