package com.chenweikeng.mcparks.showtimes;

import com.chenweikeng.mcparks.config.ModConfig;
import com.chenweikeng.mcparks.showtimes.ShowTimesSchedule.UpcomingShow;
import com.mojang.blaze3d.vertex.PoseStack;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Single right-side info panel for the MCParks "Show Times" chest GUI.
 *
 * <p>Sections, top-down chronological:
 * <ul>
 *   <li><b>Previous</b> — the most recent show that just played, with "Xh ago"</li>
 *   <li><b>Now</b> — current wall-clock time in user and server zones</li>
 *   <li><b>Next</b> — the soonest upcoming show, with countdown</li>
 *   <li><b>Upcoming</b> — the next several shows after that</li>
 * </ul>
 *
 * <p>The MCParks server splits the schedule across two pages (this-week / next-week),
 * detected by the Arrow item at slot 53 (Next) / slot 45 (Prev). The panel always
 * summarizes page 1 — when the player navigates to page 2 we keep showing the cached
 * first-page snapshot, so "what's next?" doesn't jump to last week's shows the
 * moment the player flips the page.
 *
 * <p><b>Weekday correctness:</b> the weekday for each zone is taken independently,
 * because a show at {@code Sat 4:00 PM EDT} is {@code Sun 4:00 AM SGT}.
 */
public final class ShowTimesOverlay {

    private static final DateTimeFormatter TIME_12H =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY_SHORT =
        DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter ZONE_ABBR =
        DateTimeFormatter.ofPattern("z", Locale.ENGLISH);

    /** How many shows to list in the "Upcoming" section after the "Next" slot. */
    private static final int UPCOMING_COUNT = 7;

    /** Panel body width in GUI-scaled pixels. Tuned so a worst-case zone line fits. */
    private static final int PANEL_WIDTH = 230;
    private static final int PAD = 6;
    private static final int LINE_HEIGHT = 9;
    private static final int SECTION_GAP = 4;
    /** Distance from the right edge of the screen — pulls the panel inward off the bezel. */
    private static final int MARGIN = 32;
    /** Space reserved for the countdown on upcoming-show lines, so name truncation knows where to stop. */
    private static final int COUNTDOWN_RESERVE = 60;
    private static final String ELLIPSIS = "...";

    private static final int BG_COLOR = 0xE808101E;
    private static final int BORDER_COLOR = 0xFF6FA8FF;
    private static final int HEADER_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFCCCCFF;
    private static final int SUBTLE_COLOR = 0xFFA0A8C0;
    private static final int DIVIDER_COLOR = 0x806FA8FF;

    /**
     * Snapshot of the most recently captured page-1 grid. Survives navigation
     * to page 2 so the panel keeps showing this-week data while the player
     * browses next-week. Reset only when the player visits page 1 again.
     */
    private static ShowTimesGrid cachedFirstPage = null;

    private ShowTimesOverlay() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!ShowTimesGrid.matches(screen)) return;
            ScreenEvents.afterRender(screen).register((s, pose, mx, my, tick) -> render(s, pose));
        });
    }

    private static void render(Screen screen, PoseStack pose) {
        if (!ModConfig.currentSetting.showTimesEnhancements) return;

        ShowTimesGrid current = ShowTimesGrid.capture();
        if (current.isEmpty()) return;

        // Refresh the page-1 cache whenever the player is looking at it.
        // Non-paginated grids (single-page schedules) also count as "first page".
        if (current.isFirstPage() || !current.isPaginated()) {
            cachedFirstPage = current;
        }

        // Prefer cached page-1 data; if we've never seen page 1 (player jumped
        // straight to page 2), fall back to whatever's on screen.
        ShowTimesGrid panelGrid = (cachedFirstPage != null && !cachedFirstPage.isEmpty())
            ? cachedFirstPage : current;

        ZonedDateTime now = ZonedDateTime.now();
        List<UpcomingShow> upcoming = ShowTimesSchedule.upcoming(panelGrid, now);
        List<UpcomingShow> recent = ShowTimesSchedule.recent(panelGrid, now);
        if (upcoming.isEmpty() && recent.isEmpty()) return;

        ZoneId userZone = ShowTimesTooltip.userZone();
        ZoneId serverZone = !upcoming.isEmpty() ? upcoming.get(0).header().zone()
            : !recent.isEmpty() ? recent.get(0).header().zone()
            : ZoneId.of("America/New_York");

        Font font = Minecraft.getInstance().font;
        List<PanelLine> lines = buildLines(font, recent, upcoming, now, serverZone, userZone);
        drawPanel(screen, pose, font, lines, screen.width - PANEL_WIDTH - MARGIN);
    }

    private static void drawPanel(Screen screen, PoseStack pose, Font font, List<PanelLine> lines, int panelX) {
        int totalH = PAD;
        for (PanelLine pl : lines) totalH += pl.heightPx;
        totalH += PAD;

        int panelY = Math.max(MARGIN, (screen.height - totalH) / 2);

        GuiComponent.fill(pose, panelX, panelY, panelX + PANEL_WIDTH, panelY + totalH, BG_COLOR);
        GuiComponent.fill(pose, panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, BORDER_COLOR);
        GuiComponent.fill(pose, panelX, panelY + totalH - 1, panelX + PANEL_WIDTH, panelY + totalH, BORDER_COLOR);
        GuiComponent.fill(pose, panelX, panelY, panelX + 1, panelY + totalH, BORDER_COLOR);
        GuiComponent.fill(pose, panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + totalH, BORDER_COLOR);

        int y = panelY + PAD;
        for (PanelLine pl : lines) {
            if (pl.divider) {
                GuiComponent.fill(pose, panelX + PAD, y + pl.heightPx / 2,
                    panelX + PANEL_WIDTH - PAD, y + pl.heightPx / 2 + 1, DIVIDER_COLOR);
            } else if (pl.component != null) {
                font.draw(pose, pl.component, panelX + PAD, y, pl.color);
            }
            y += pl.heightPx;
        }
    }

    private static List<PanelLine> buildLines(
        Font font, List<UpcomingShow> recent, List<UpcomingShow> upcoming,
        ZonedDateTime now, ZoneId serverZone, ZoneId userZone
    ) {
        List<PanelLine> lines = new ArrayList<>();
        boolean splitZones = !userZone.equals(serverZone);
        int innerWidth = PANEL_WIDTH - PAD * 2;

        lines.add(PanelLine.text(
            Component.literal("Show Times").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), HEADER_COLOR));
        lines.add(PanelLine.divider());

        // --- Previous ---
        if (!recent.isEmpty()) {
            UpcomingShow prev = recent.get(0);
            Duration ago = Duration.between(prev.whenServerZone(), now);
            lines.add(PanelLine.text(
                sectionLabel("PREVIOUS", ChatFormatting.LIGHT_PURPLE, ShowTimesSchedule.formatTimeAgo(ago), ChatFormatting.GRAY),
                LABEL_COLOR));
            lines.add(PanelLine.text(
                truncate(font, prev.name(), innerWidth, true).withStyle(ChatFormatting.WHITE), 0xFFFFFFFF));
            lines.add(PanelLine.text(zoneLine(prev.whenServerZone(), userZone, splitZones), 0xFFFFFFFF));
            lines.add(PanelLine.gap(SECTION_GAP));
        }

        // --- Now ---
        lines.add(PanelLine.text(Component.literal("NOW").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), LABEL_COLOR));
        lines.add(PanelLine.text(zoneLine(now.withZoneSameInstant(serverZone), userZone, splitZones), 0xFFFFFFFF));
        lines.add(PanelLine.gap(SECTION_GAP));

        // --- Next ---
        if (!upcoming.isEmpty()) {
            UpcomingShow next = upcoming.get(0);
            Duration untilNext = Duration.between(now, next.whenServerZone());
            String countdown = ShowTimesSchedule.formatCountdownPrecise(untilNext);
            ChatFormatting countdownColor = untilNext.toMinutes() < 60 ? ChatFormatting.GOLD : ChatFormatting.AQUA;
            lines.add(PanelLine.text(
                sectionLabel("NEXT", ChatFormatting.YELLOW, countdown, countdownColor), LABEL_COLOR));
            lines.add(PanelLine.text(
                truncate(font, next.name(), innerWidth, true).withStyle(ChatFormatting.WHITE), 0xFFFFFFFF));
            lines.add(PanelLine.text(zoneLine(next.whenServerZone(), userZone, splitZones), 0xFFFFFFFF));
        }

        // --- Upcoming ---
        if (upcoming.size() > 1) {
            lines.add(PanelLine.gap(SECTION_GAP));
            lines.add(PanelLine.divider());
            lines.add(PanelLine.text(Component.literal("UPCOMING").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), LABEL_COLOR));
            int end = Math.min(1 + UPCOMING_COUNT, upcoming.size());
            for (int i = 1; i < end; i++) {
                UpcomingShow u = upcoming.get(i);
                Duration until = Duration.between(now, u.whenServerZone());
                String countdown = ShowTimesSchedule.formatCountdown(until);
                ChatFormatting countdownColor = until.toMinutes() < 60
                    ? ChatFormatting.GOLD
                    : until.toHours() < 24 ? ChatFormatting.AQUA : ChatFormatting.DARK_AQUA;

                String truncatedName = truncateString(font, u.name(), innerWidth - COUNTDOWN_RESERVE);
                MutableComponent nameLine = Component.literal("• ")
                    .withStyle(ChatFormatting.BLUE)
                    .append(Component.literal(truncatedName).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(countdown).withStyle(countdownColor));
                lines.add(PanelLine.text(nameLine, 0xFFFFFFFF));
                lines.add(PanelLine.text(
                    Component.literal("  ").append(zoneLine(u.whenServerZone(), userZone, splitZones)),
                    SUBTLE_COLOR));
            }
        }

        return lines;
    }

    /** {@code "<weekday> <h:mm a> <abbr> (<weekday> <h:mm a> <abbr>)"} — user zone first (bright); server in parens (dim). */
    private static MutableComponent zoneLine(ZonedDateTime serverTime, ZoneId userZone, boolean splitZones) {
        if (!splitZones) {
            return Component.literal(formatTimeWithDay(serverTime)).withStyle(ChatFormatting.WHITE);
        }
        ZonedDateTime userTime = serverTime.withZoneSameInstant(userZone);
        return Component.literal(formatTimeWithDay(userTime)).withStyle(ChatFormatting.WHITE)
            .append(Component.literal(" (" + formatTimeWithDay(serverTime) + ")").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String formatTimeWithDay(ZonedDateTime zdt) {
        return zdt.format(WEEKDAY_SHORT) + " " + TIME_12H.format(zdt) + " " + zdt.format(ZONE_ABBR);
    }

    /** {@code "<LABEL> · <suffix>"} — label in {@code labelColor}, separator dim, suffix in {@code suffixColor}. */
    private static Component sectionLabel(String label, ChatFormatting labelColor, String suffix, ChatFormatting suffixColor) {
        return Component.literal(label).withStyle(labelColor, ChatFormatting.BOLD)
            .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(suffix).withStyle(suffixColor));
    }

    /** Truncates {@code text} to fit in {@code maxWidth}, appending {@code "..."} if cut. */
    private static String truncateString(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        int ellipsisWidth = font.width(ELLIPSIS);
        String trimmed = font.plainSubstrByWidth(text, maxWidth - ellipsisWidth, false);
        return trimmed + ELLIPSIS;
    }

    /** Truncates and wraps in a MutableComponent (bold optional via the {@code bold} flag). */
    private static MutableComponent truncate(Font font, String text, int maxWidth, boolean bold) {
        String t = truncateString(font, text, maxWidth);
        MutableComponent c = Component.literal(t);
        if (bold) c = c.withStyle(ChatFormatting.BOLD);
        return c;
    }

    /** One rendered row in the panel. */
    private static final class PanelLine {
        final Component component;
        final int color;
        final int heightPx;
        final boolean divider;

        private PanelLine(Component c, int color, int heightPx, boolean divider) {
            this.component = c;
            this.color = color;
            this.heightPx = heightPx;
            this.divider = divider;
        }

        static PanelLine text(Component c, int color) {
            return new PanelLine(c, color, LINE_HEIGHT, false);
        }
        static PanelLine divider() {
            return new PanelLine(null, 0, 4, true);
        }
        static PanelLine gap(int px) {
            return new PanelLine(null, 0, px, false);
        }
    }
}
