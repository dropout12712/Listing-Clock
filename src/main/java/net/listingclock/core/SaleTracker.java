package net.listingclock.core;
//

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Pending attempts are session-only; only positively acknowledged sales become listings. */
public final class SaleTracker {
    public static final long TIMEOUT_NANOS = Duration.ofSeconds(90).toNanos();
    private static final Pattern COMMAND = Pattern.compile("^/?ah\\s+sell\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELL_ATTEMPT = Pattern.compile("^/?ah\\s+sell(?:\\s|$).*", Pattern.CASE_INSENSITIVE);
    private final List<Pending> pending = new ArrayList<>();
    private final ConfirmationParser parser = new ConfirmationParser();

    public record ItemSnapshot(String name, String type, int quantity) {
        public ItemSnapshot {
            if (name == null || name.isBlank() || name.length() > 512 || type == null
                    || !type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || quantity < 1 || quantity > 999999) {
                throw new IllegalArgumentException("Invalid held item");
            }
        }
    }
    private record Pending(UUID owner, ItemSnapshot item, BigDecimal price, long sentNanos) { }

    public boolean command(String command, ItemSnapshot item, UUID owner, long nanos) {
        expire(nanos);
        var m = COMMAND.matcher(command);
        if (!m.matches() || item == null || owner == null) {
            if (SELL_ATTEMPT.matcher(command).matches()) pending.clear();
            return false;
        }
        Optional<BigDecimal> price = Price.parse(m.group(1));
        if (price.isEmpty()) { pending.clear(); return false; }
        // Keep overlapping attempts separate; acknowledge only an unambiguous item/price match.
        if (pending.size() >= 16) { pending.clear(); return false; }
        pending.add(new Pending(owner, item, price.get(), nanos));
        return true;
    }

    public Optional<Listing> message(String message, UUID owner, long nowMillis, long nanos) {
        expire(nanos);
        if (pending.isEmpty()) return Optional.empty();
        if (parser.failure(message)) {
            pending.clear();
            return Optional.empty();
        }
        var result = parser.success(message);
        if (result.isEmpty()) return Optional.empty();
        var confirmation = result.get();
        List<Pending> matches = pending.stream().filter(p -> p.owner().equals(owner))
                .filter(p -> confirmation.price() == null || p.price().compareTo(confirmation.price()) == 0)
                .filter(p -> itemMatches(confirmation.item(), p.item())).toList();
        if (matches.size() != 1) {
            // A recognized but mismatched acknowledgment cannot be reused for a later attempt.
            pending.clear();
            return Optional.empty();
        }
        Pending sale = matches.getFirst();
        pending.remove(sale);
        return Optional.of(new Listing(UUID.randomUUID(), owner, sale.item().name(), sale.item().type(),
                sale.item().quantity(), sale.price().toPlainString(), nowMillis));
    }

    private static boolean itemMatches(String raw, ItemSnapshot snapshot) {
        String item = MessageNormalizer.normalize(raw);
        if (item.matches("(?:your |the )?items?")) return true;
        String name = MessageNormalizer.normalize(snapshot.name());
        if (item.equals(name)) return true;
        var quantity = Pattern.compile("^(\\d+)(?:x| ×)? (.+)$").matcher(item);
        return quantity.matches() && quantity.group(1).equals(Integer.toString(snapshot.quantity()))
                && quantity.group(2).equals(name);
    }

    public int expire(long nanos) {
        int before = pending.size();
        pending.removeIf(p -> nanos - p.sentNanos() >= TIMEOUT_NANOS);
        return before - pending.size();
    }

    public void reset() { pending.clear(); }
    public int pendingCount() { return pending.size(); }
}
