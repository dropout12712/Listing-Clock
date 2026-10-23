package net.listingclock.core;
//

import java.text.Normalizer;
import java.util.Locale;

public final class MessageNormalizer {
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢ";

    private MessageNormalizer() { }

    public static String normalize(String raw) {
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replaceAll("(?i)§[0-9a-fk-orx]", "")
                .replaceAll("[\\p{Cf}]", "").replace('\u2019', '\'').replace('\u00a0', ' ')
                .toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = SMALL.indexOf(c);
            out.append(index >= 0 ? (char) ('a' + index) : c);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }
}
