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
import net.minecraft.network.chat.contents.LiteralContents;

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

    /** Matches a legacy formatting code: §0–9, §a–f, §k–r (case-insensitive). */
    private static final Pattern LEGACY_CODE = Pattern.compile("\u00a7[0-9a-fk-orA-FK-OR]");

    private ChatColorRewriter() {}

    public static Component rewrite(Component in) {
        if (in == null) return in;
        List<Component> siblings = in.getSiblings();
        if (siblings.isEmpty()) return in;
        int arrowIdx = findArrowIndex(siblings);
        if (arrowIdx < 0) return in;

        RankMatch rank = findRankPrefix(in, siblings, arrowIdx);

        // When a rank matches we rebuild from a fresh root because the
        // original root contents may carry the rank prefix as raw legacy-
        // coded literal text (the "Cast Member" format,
        // §4[§cCast Member§4]…). For sibling-based ranks the original root
        // is empty, so this is a no-op aside from the cloned style.
        MutableComponent out = (rank != null)
                ? Component.empty().setStyle(in.getStyle())
                : mutableCopy(in);
        out.getSiblings().clear();

        int i = 0;
        if (rank != null) {
            out.append(buildBadge(rank.entry));
            // endIdx is -1 when the prefix lived entirely in root contents
            // (no siblings consumed); otherwise drop siblings 0..endIdx.
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
     * Look for a {@code [Rank]} prefix in two places, in this order:
     *
     * <ol>
     *   <li>The root component's literal contents — used by MCParks for
     *       Cast-Member-style messages where the bracket arrives as
     *       {@code §4[§cCast Member§4]} embedded in root text rather than
     *       split into styled siblings. Legacy {@code §x} codes are
     *       stripped before matching.</li>
     *   <li>The sibling list, accumulated from the start — used by
     *       Guest/AP/Club 33/etc. messages where the bracket is split
     *       into per-character styled siblings.</li>
     * </ol>
     *
     * @return a {@link RankMatch} with {@code endIdx == -1} when the
     *         prefix lived in root contents; with {@code endIdx == i}
     *         when sibling {@code i} contained the closing {@code ]};
     *         or {@code null} if no known rank was found before the arrow.
     */
    private static RankMatch findRankPrefix(Component in, List<Component> siblings, int arrowIdx) {
        if (RankBadges.isEmpty()) return null;

        String rootText = literalText(in);
        if (!rootText.isEmpty()) {
            String stripped = LEGACY_CODE.matcher(rootText).replaceAll("");
            if (stripped.indexOf(']') >= 0) {
                Matcher m = RANK_PREFIX.matcher(stripped);
                if (m.find()) {
                    RankBadges.Entry entry = RankBadges.get(m.group(1));
                    return entry != null ? new RankMatch(entry, -1) : null;
                }
            }
        }

        StringBuilder acc = new StringBuilder();
        for (int i = 0; i <= arrowIdx && i < siblings.size(); i++) {
            acc.append(siblings.get(i).getString());
            int closeBracket = acc.indexOf("]");
            if (closeBracket < 0) continue;

            Matcher m = RANK_PREFIX.matcher(acc);
            if (!m.find()) return null;
            RankBadges.Entry entry = RankBadges.get(m.group(1));
            if (entry == null) return null;

            return new RankMatch(entry, i);
        }
        return null;
    }

    private static String literalText(Component c) {
        ComponentContents contents = c.getContents();
        return (contents instanceof LiteralContents lit) ? lit.text() : "";
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
