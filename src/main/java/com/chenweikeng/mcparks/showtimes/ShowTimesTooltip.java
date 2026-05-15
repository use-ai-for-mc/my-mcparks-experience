package com.chenweikeng.mcparks.showtimes;

import com.chenweikeng.mcparks.config.ModConfig;
import com.chenweikeng.mcparks.showtimes.ShowTimesSchedule.ParsedHeader;
import com.chenweikeng.mcparks.showtimes.ShowTimesSchedule.UpcomingShow;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Registers an {@link ItemTooltipCallback} that adds timezone-converted lines
 * to items in the {@link ShowTimesGrid#SCREEN_TITLE Show Times} chest GUI.
 *
 * <p>Two insertions:
 * <ul>
 *   <li>Time-header shovels get {@code "4:00 AM PDT · in 2h 14m"}.</li>
 *   <li>Show cells get the same, plus the day-of-week label since a cell
 *       alone doesn't carry its row context.</li>
 * </ul>
 */
public final class ShowTimesTooltip {

    private static final DateTimeFormatter TIME_12H =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY_SHORT =
        DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    private ShowTimesTooltip() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, ctx, lines) -> {
            if (!ModConfig.currentSetting.showTimesEnhancements) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null || !ShowTimesGrid.matches(mc.screen)) return;
            augment(stack, lines);
        });
    }

    private static void augment(ItemStack stack, List<Component> lines) {
        String name = stack.getHoverName().getString();
        ShowTimesGrid grid = ShowTimesGrid.capture();
        if (grid.isEmpty()) return;
        ZoneId userZone = userZone();
        ZonedDateTime now = ZonedDateTime.now();

        Optional<ParsedHeader> asHeader = ShowTimesSchedule.parseTimeHeader(name);
        if (asHeader.isPresent()) {
            appendHeaderLines(lines, asHeader.get(), userZone, now);
            return;
        }

        for (UpcomingShow u : ShowTimesSchedule.upcoming(grid, now)) {
            if (u.name().equals(name)) {
                appendShowCellLines(lines, u, userZone, now);
                return;
            }
        }
    }

    private static void appendHeaderLines(List<Component> lines, ParsedHeader h, ZoneId userZone, ZonedDateTime now) {
        if (userZone.equals(h.zone())) return;
        ZonedDateTime today = ShowTimesSchedule.nextOccurrence(
            now.getDayOfWeek(), h.time(), h.zone(), now.minusDays(1));
        ZonedDateTime local = today.withZoneSameInstant(userZone);
        String abbr = local.format(DateTimeFormatter.ofPattern("z", Locale.ENGLISH));
        String localTime = TIME_12H.format(local);
        lines.add(Component.empty());
        lines.add(Component.literal("Your time: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(localTime + " " + abbr).withStyle(ChatFormatting.AQUA)));
    }

    private static void appendShowCellLines(List<Component> lines, UpcomingShow u, ZoneId userZone, ZonedDateTime now) {
        Duration until = Duration.between(now, u.whenServerZone());
        ZonedDateTime serverLocal = u.whenServerZone();
        ZonedDateTime userLocal = serverLocal.withZoneSameInstant(userZone);

        // CRITICAL: the weekday differs between zones whenever the show crosses midnight.
        // Each line must use the weekday of its OWN zone — never mix.
        String serverAbbr = serverLocal.format(DateTimeFormatter.ofPattern("z", Locale.ENGLISH));
        String userAbbr = userLocal.format(DateTimeFormatter.ofPattern("z", Locale.ENGLISH));
        String serverWeekday = serverLocal.format(WEEKDAY_SHORT);
        String userWeekday = userLocal.format(WEEKDAY_SHORT);

        lines.add(Component.empty());
        MutableComponent next = Component.literal("Next: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(serverWeekday + " " + TIME_12H.format(serverLocal) + " " + serverAbbr)
                .withStyle(ChatFormatting.WHITE));
        lines.add(next);

        if (!userZone.equals(u.header().zone())) {
            lines.add(Component.literal("Your time: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(userWeekday + " " + TIME_12H.format(userLocal) + " " + userAbbr)
                    .withStyle(ChatFormatting.AQUA)));
        }

        lines.add(Component.literal(ShowTimesSchedule.formatCountdown(until))
            .withStyle(until.toMinutes() < 60 ? ChatFormatting.GOLD : ChatFormatting.DARK_AQUA));
    }

    /** Resolves the user's preferred display zone: config override if set, else system default. */
    public static ZoneId userZone() {
        String override = ModConfig.currentSetting.showTimesTimezone;
        if (override != null && !override.isBlank()) {
            try { return ZoneId.of(override.trim()); }
            catch (Exception ignored) { /* fall through to system default */ }
        }
        return ZoneId.systemDefault();
    }
}
