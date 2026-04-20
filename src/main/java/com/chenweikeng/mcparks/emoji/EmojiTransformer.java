package com.chenweikeng.mcparks.emoji;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Rewrites a received {@link Component} so that {@code :shortcode:} sequences
 * and literal Unicode emoji become PUA-coded children styled with the
 * {@code my-mcparks-experience:emoji} bitmap font. Returns the same instance
 * when no rewrite is needed so it's safe to call on every incoming message.
 */
public final class EmojiTransformer {

    private static final Pattern SHORTCODE = Pattern.compile(":([a-z0-9_+-]+):");

    // Broad catch for emoji codepoints, surrogate pairs, ZWJ sequences, and VS-16.
    // Java regex's \x{...} accepts supplementary codepoints directly.
    private static final Pattern UNICODE_EMOJI = Pattern.compile(
        "[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}\\x{2300}-\\x{23FF}\\x{2B00}-\\x{2BFF}\\x{3000}-\\x{303F}\\x{3200}-\\x{32FF}]" +
        "(?:\\x{FE0F}?(?:\\x{200D}[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}]\\x{FE0F}?)*)?"
    );

    /**
     * Western ASCII emoticons -> gemoji shortcode name. A preprocessing pass
     * rewrites matches into {@code :shortcode:} form, so the existing
     * shortcode pipeline does the actual PUA substitution. Keys are ordered
     * longest-first so patterns like {@code :-)} beat the shorter {@code :)}.
     */
    private static final Map<String, String> EMOTICONS = buildEmoticonMap();

    private static Map<String, String> buildEmoticonMap() {
        // LinkedHashMap to preserve insertion order for longest-first regex build.
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        // 3-char (must come before any 2-char prefix they overlap with)
        m.put(":'(", "cry");
        m.put(">:(", "angry");
        m.put(">:)", "imp");
        m.put("O:)", "innocent");
        m.put("o:)", "innocent");
        m.put("</3", "broken_heart");
        m.put(":-)", "slightly_smiling_face");
        m.put(":-(", "slightly_frowning_face");
        m.put(":-D", "smiley");
        m.put(";-)", "wink");
        m.put(":-P", "stuck_out_tongue");
        m.put(":-p", "stuck_out_tongue");
        m.put(":-O", "open_mouth");
        m.put(":-o", "open_mouth");
        m.put(":-/", "confused");
        m.put(":-|", "neutral_face");
        m.put("8-)", "sunglasses");
        m.put("B-)", "sunglasses");
        m.put("T_T", "sob");
        m.put("T.T", "sob");
        m.put(";_;", "sob");
        m.put(";-;", "sob");
        // 2-char
        m.put(":)", "slightly_smiling_face");
        m.put(":]", "slightly_smiling_face");
        m.put(":(", "slightly_frowning_face");
        m.put(":[", "slightly_frowning_face");
        m.put(":D", "smiley");
        m.put(";)", "wink");
        m.put(":P", "stuck_out_tongue");
        m.put(":p", "stuck_out_tongue");
        m.put("xD", "laughing");
        m.put("XD", "laughing");
        m.put(":O", "open_mouth");
        m.put(":o", "open_mouth");
        m.put(":/", "confused");
        m.put(":|", "neutral_face");
        m.put(":3", "smiley_cat");
        m.put("<3", "heart");
        m.put("8)", "sunglasses");
        m.put("B)", "sunglasses");
        return m;
    }

    /**
     * Matches any known emoticon at a word boundary: preceded by start-of-string
     * or whitespace, and followed by end-of-string, whitespace, or common
     * sentence/bracket punctuation. This avoids false matches inside URLs
     * ({@code http://}), file paths ({@code C:/}), and normal words.
     */
    private static final Pattern EMOTICON = Pattern.compile(
        "(?:^|(?<=\\s))(" + buildEmoticonAlternation() + ")(?=$|\\s|[.,!?;:)\\]}])"
    );

    private static String buildEmoticonAlternation() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String key : EMOTICONS.keySet()) {
            if (!first) sb.append('|');
            sb.append(Pattern.quote(key));
            first = false;
        }
        return sb.toString();
    }

    private EmojiTransformer() {}

    public static Component transform(Component in) {
        if (in == null) return null;
        if (EmojiAssets.SHORTCODES.isEmpty() && EmojiAssets.UNICODE_MAP.isEmpty()) {
            return in;
        }
        if (!needsRewrite(in)) {
            return in;
        }
        return rewrite(in);
    }

    /** Fast reject: walk the tree looking for anything that might contain an emoji. */
    private static boolean needsRewrite(Component c) {
        ComponentContents contents = c.getContents();
        if (contents instanceof LiteralContents lit) {
            if (stringNeedsRewrite(lit.text())) return true;
        } else if (contents instanceof TranslatableContents tr) {
            for (Object arg : tr.getArgs()) {
                if (arg instanceof Component ac && needsRewrite(ac)) return true;
                if (arg instanceof String s && stringNeedsRewrite(s)) return true;
            }
        }
        for (Component sibling : c.getSiblings()) {
            if (needsRewrite(sibling)) return true;
        }
        return false;
    }

    private static boolean stringNeedsRewrite(String s) {
        if (s == null || s.isEmpty()) return false;
        if (s.indexOf(':') >= 0 && SHORTCODE.matcher(s).find()) return true;
        if (EMOTICON.matcher(s).find()) return true;
        // Cheap unicode pre-filter: any char outside BMP Latin/Punct range triggers full check
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x2300 || Character.isHighSurrogate(ch)) {
                if (UNICODE_EMOJI.matcher(s).find()) return true;
                break;
            }
        }
        return false;
    }

    /**
     * Preprocessing: rewrite ASCII emoticons (bounded by whitespace/start/end
     * or common punctuation) into {@code :shortcode:} form. The existing
     * shortcode pass then swaps them for PUA characters.
     */
    private static String expandEmoticons(String s) {
        if (s.isEmpty()) return s;
        Matcher m = EMOTICON.matcher(s);
        if (!m.find()) return s;
        StringBuilder out = new StringBuilder(s.length() + 16);
        int cursor = 0;
        do {
            String shortcode = EMOTICONS.get(m.group(1));
            if (shortcode != null && EmojiAssets.SHORTCODES.containsKey(shortcode)) {
                out.append(s, cursor, m.start(1));
                out.append(':').append(shortcode).append(':');
                cursor = m.end(1);
            }
        } while (m.find());
        out.append(s, cursor, s.length());
        return out.toString();
    }

    private static Component rewrite(Component in) {
        ComponentContents contents = in.getContents();
        Style style = in.getStyle();
        MutableComponent out;

        if (contents instanceof LiteralContents lit) {
            out = rewriteString(lit.text(), style);
        } else if (contents instanceof TranslatableContents tr) {
            Object[] rawArgs = tr.getArgs();
            Object[] newArgs = new Object[rawArgs.length];
            for (int i = 0; i < rawArgs.length; i++) {
                Object a = rawArgs[i];
                if (a instanceof Component ac) {
                    newArgs[i] = transform(ac);
                } else if (a instanceof String s) {
                    newArgs[i] = rewriteString(s, Style.EMPTY);
                } else {
                    newArgs[i] = a;
                }
            }
            out = MutableComponent.create(new TranslatableContents(tr.getKey(), newArgs))
                    .setStyle(style);
        } else {
            // Keep other content types (scores, selectors, keybinds, data) intact.
            out = MutableComponent.create(contents).setStyle(style);
        }

        for (Component sibling : in.getSiblings()) {
            out.append(transform(sibling));
        }
        return out;
    }

    /**
     * Rewrites a plain string, returning a MutableComponent whose outer style
     * is {@code baseStyle}. Emoji spans get an inner style with the emoji
     * font applied — so the outer chat colour/formatting is preserved on
     * normal text and the emoji renders via the bitmap font provider.
     */
    private static MutableComponent rewriteString(String s, Style baseStyle) {
        MutableComponent result = Component.empty().setStyle(baseStyle);
        Style emojiStyle = Style.EMPTY.withFont(EmojiAssets.EMOJI_FONT);

        // Preprocess: rewrite ASCII emoticons to their :shortcode: equivalents
        // so the shortcode pass below does the actual PUA substitution.
        s = expandEmoticons(s);

        // First split on shortcodes to build a list of (text, isEmoji) segments.
        List<Seg> segs = new ArrayList<>();
        Matcher m = SHORTCODE.matcher(s);
        int cursor = 0;
        while (m.find()) {
            if (m.start() > cursor) segs.add(Seg.text(s.substring(cursor, m.start())));
            String pua = EmojiAssets.SHORTCODES.get(m.group(1));
            if (pua != null) {
                segs.add(Seg.emoji(pua));
            } else {
                segs.add(Seg.text(m.group())); // unknown shortcode, keep as-is
            }
            cursor = m.end();
        }
        if (cursor < s.length()) segs.add(Seg.text(s.substring(cursor)));

        // Now expand each text segment further for Unicode emoji.
        for (Seg seg : segs) {
            if (seg.emoji) {
                result.append(Component.literal(seg.value).setStyle(emojiStyle));
            } else {
                appendWithUnicodeEmoji(result, seg.value, baseStyle, emojiStyle);
            }
        }
        return result;
    }

    private static void appendWithUnicodeEmoji(MutableComponent result, String s, Style baseStyle, Style emojiStyle) {
        if (s.isEmpty()) return;
        Matcher m = UNICODE_EMOJI.matcher(s);
        int cursor = 0;
        while (m.find()) {
            if (m.start() > cursor) {
                result.append(Component.literal(s.substring(cursor, m.start())).setStyle(baseStyle));
            }
            String match = m.group();
            String pua = EmojiAssets.UNICODE_MAP.get(match);
            if (pua == null) {
                // Retry with FE0F stripped (variation selector-16)
                String stripped = match.replace("\uFE0F", "");
                pua = EmojiAssets.UNICODE_MAP.get(stripped);
            }
            if (pua != null) {
                result.append(Component.literal(pua).setStyle(emojiStyle));
            } else {
                result.append(Component.literal(match).setStyle(baseStyle));
            }
            cursor = m.end();
        }
        if (cursor < s.length()) {
            result.append(Component.literal(s.substring(cursor)).setStyle(baseStyle));
        }
    }

    private static final class Seg {
        final String value;
        final boolean emoji;
        private Seg(String v, boolean e) { this.value = v; this.emoji = e; }
        static Seg text(String v)  { return new Seg(v, false); }
        static Seg emoji(String v) { return new Seg(v, true); }
    }
}
