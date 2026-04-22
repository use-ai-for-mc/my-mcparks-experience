package com.chenweikeng.mcparks.chat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Two things in one pass on chat-style messages (those containing a
 * {@code ›} arrow):
 * <ol>
 *   <li>Replace the leading {@code [Rank]} text prefix with a bitmap "pill"
 *       badge glyph drawn from {@link RankBadges#RANKS_FONT}, and recolor
 *       the username span to the rank's fill colour.</li>
 *   <li>Recolor every subsequent {@code gray} sibling (the message body)
 *       to white, so non-staff chat becomes as readable as staff chat.</li>
 * </ol>
 * Messages without a {@code ›} arrow are passed through untouched.
 */
public final class ChatColorRewriter {

    private static final TextColor GRAY  = TextColor.fromLegacyFormat(ChatFormatting.GRAY);
    private static final TextColor WHITE = TextColor.fromLegacyFormat(ChatFormatting.WHITE);
    private static final String ARROW = "\u203A"; // ›

    /** Matches a leading {@code [rank]} at the start of accumulated sibling text. */
    private static final Pattern RANK_PREFIX = Pattern.compile("^\\s*\\[([^\\]\\r\\n]{1,40})\\]");

    private ChatColorRewriter() {}

    public static Component rewrite(Component in) {
        if (in == null) return in;
        List<Component> siblings = in.getSiblings();
        if (siblings.isEmpty()) return in;
        int arrowIdx = findArrowIndex(siblings);
        if (arrowIdx < 0) return in;

        RankMatch rank = findRankPrefix(siblings, arrowIdx);

        MutableComponent out = mutableCopy(in);
        out.getSiblings().clear();

        int i = 0;
        if (rank != null) {
            // Pill badge replaces siblings[0..rank.endIdx]
            out.append(buildBadge(rank.entry));
            i = rank.endIdx + 1;

            // Recolor username span (between rank close and arrow) with fill colour.
            TextColor nameColor = hexToTextColor(rank.entry.fill);
            for (; i <= arrowIdx; i++) {
                out.append(recolor(siblings.get(i), nameColor));
            }
            i = arrowIdx + 1;
        } else {
            // Pass siblings through up to and including the arrow.
            for (; i <= arrowIdx; i++) {
                out.append(siblings.get(i));
            }
            i = arrowIdx + 1;
        }

        // Post-arrow: whiten any gray sibling (message body).
        for (; i < siblings.size(); i++) {
            Component sib = siblings.get(i);
            out.append(hasGrayColor(sib) ? recolor(sib, WHITE) : sib);
        }
        return out;
    }

    private static int findArrowIndex(List<Component> siblings) {
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getString().contains(ARROW)) return i;
        }
        return -1;
    }

    private static boolean hasGrayColor(Component c) {
        return GRAY.equals(c.getStyle().getColor());
    }

    /**
     * Walk siblings from the start, accumulating their rendered text, until
     * we've captured a {@code [Rank]} prefix. Returns the matched badge and
     * the sibling index that contains the closing {@code ]}, or null if no
     * known rank is present before the arrow.
     */
    private static RankMatch findRankPrefix(List<Component> siblings, int arrowIdx) {
        if (RankBadges.isEmpty()) return null;

        StringBuilder acc = new StringBuilder();
        for (int i = 0; i <= arrowIdx && i < siblings.size(); i++) {
            acc.append(siblings.get(i).getString());
            int closeBracket = acc.indexOf("]");
            if (closeBracket < 0) continue;

            Matcher m = RANK_PREFIX.matcher(acc);
            if (!m.find()) return null;
            RankBadges.Entry entry = RankBadges.get(m.group(1));
            if (entry == null) return null;

            // Require that the closing ']' lives inside this sibling so we can
            // safely drop everything up to and including i.
            return new RankMatch(entry, i);
        }
        return null;
    }

    private static Component buildBadge(RankBadges.Entry entry) {
        Style s = Style.EMPTY.withFont(RankBadges.RANKS_FONT);
        return Component.literal(entry.charStr).setStyle(s);
    }

    private static Component recolor(Component c, TextColor color) {
        Style newStyle = c.getStyle().withColor(color);
        MutableComponent copy = MutableComponent.create(c.getContents()).setStyle(newStyle);
        for (Component sib : c.getSiblings()) {
            copy.append(recolor(sib, color));
        }
        return copy;
    }

    private static MutableComponent mutableCopy(Component c) {
        ComponentContents contents = c.getContents();
        return MutableComponent.create(contents).setStyle(c.getStyle());
    }

    private static TextColor hexToTextColor(String hex) {
        // "#RRGGBB" -> int
        String body = hex.startsWith("#") ? hex.substring(1) : hex;
        return TextColor.fromRgb(Integer.parseInt(body, 16));
    }

    private static final class RankMatch {
        final RankBadges.Entry entry;
        final int endIdx;
        RankMatch(RankBadges.Entry e, int i) { this.entry = e; this.endIdx = i; }
    }
}
