package net.listingclock.core;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/** Anchored server-feedback templates. Never feed player chat into this parser. */
public final class ConfirmationParser {
    private static final String PREFIX = "^(?:(?:\\[(?:auction(?: house)?|ah|donutsmp)\\]|auction(?: house)?|ah) ?(?:[»›:>|→] ?)?|[»›→] ?)?";
    private static final String PRICE = "(?<price>(?:\\$\\s*)?" + Price.TOKEN + ")";
    private static final String END = "[.!]?$";
    private static final Pattern LISTED = Pattern.compile(PREFIX
            + "(?:you (?:have )?(?:successfully )?listed|successfully listed) "
            + "(?<item>.+?) for " + PRICE + "(?: (?:on|in) (?:the )?(?:auction house|ah))?" + END);
    private static final Pattern ITEM_LISTED = Pattern.compile(PREFIX
            + "your (?<item>.+?) (?:has|have) been (?:successfully )?listed"
            + "(?: (?:on|in) (?:the )?(?:auction house|ah))? for " + PRICE + END);
    private static final Pattern NO_PRICE = Pattern.compile(PREFIX
            + "(?:you (?:have )?(?:successfully )?listed your item(?:s)?|your item(?:s)? (?:has|have) been (?:successfully )?listed)"
            + " (?:on|in) (?:the )?(?:auction house|ah)" + END);
    private static final Pattern FAILURE = Pattern.compile(PREFIX
            + "(?:you (?:cannot|can't|can not|could not|must|need|already|have too many|have reached|don't have|do not have|aren't|are not)|"
            + "(?:auction|listing) (?:failed|limit)|failed to (?:create|list)|(?:please )?(?:hold|wait)|"
            + "(?:invalid|minimum|maximum) (?:price|amount)|(?:this|that) item (?:cannot|can't)|"
            + "(?:your )?(?:auction house|auction|ah) (?:is full|limit)|not enough).*");

    public record Confirmation(String item, BigDecimal price) { }

    public Optional<Confirmation> success(String raw) {
        if (raw == null || raw.length() > 2048) return Optional.empty();
        String text = MessageNormalizer.normalize(raw);
        for (Pattern pattern : new Pattern[]{LISTED, ITEM_LISTED}) {
            var m = pattern.matcher(text);
            if (m.matches()) {
                var price = Price.parse(m.group("price"));
                if (price.isPresent()) return Optional.of(new Confirmation(m.group("item"), price.get()));
            }
        }
        return NO_PRICE.matcher(text).matches() ? Optional.of(new Confirmation("your item", null)) : Optional.empty();
    }

    public boolean failure(String raw) {
        return raw != null && raw.length() <= 2048 && FAILURE.matcher(MessageNormalizer.normalize(raw)).matches();
    }
}
