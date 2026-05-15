package com.chenweikeng.mcparks.showtimes;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the chest-GUI "Show Times" board and answers "what's next?".
 *
 * <p>The server publishes the schedule as plain-English strings in two axes:
 * <ul>
 *   <li>Time column headers like {@code "7:00 AM Eastern"}</li>
 *   <li>Day row labels like {@code "Friday"}</li>
 * </ul>
 * No structured NBT exists, so we parse text and map {@code Eastern} to
 * {@link ZoneId#of(String)} {@code "America/New_York"} (DST-aware).
 */
public final class ShowTimesSchedule {

    /** {@code "7:00 AM Eastern"} or {@code "12:00 AM Eastern"}. */
    private static final Pattern TIME_HEADER = Pattern.compile(
        "^\\s*(\\d{1,2}):(\\d{2})\\s+(AM|PM)\\s+(\\w+)\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    private static final DateTimeFormatter DISPLAY_12H =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private ShowTimesSchedule() {}

    /** Parses a time header's display name. Returns empty if the string doesn't match. */
    public static Optional<ParsedHeader> parseTimeHeader(String raw) {
        if (raw == null) return Optional.empty();
        Matcher m = TIME_HEADER.matcher(raw);
        if (!m.matches()) return Optional.empty();
        int hour = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        boolean pm = m.group(3).equalsIgnoreCase("PM");
        if (hour == 12) hour = 0;
        if (pm) hour += 12;
        LocalTime time = LocalTime.of(hour, min);
        ZoneId zone = zoneForLabel(m.group(4));
        return Optional.of(new ParsedHeader(time, m.group(4), zone));
    }

    /** Parses a day label like {@code "Friday"}. Returns empty if unknown. */
    public static Optional<DayOfWeek> parseDayLabel(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim().toUpperCase(Locale.ENGLISH);
        try {
            return Optional.of(DayOfWeek.valueOf(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Maps a human label to an IANA zone. Only "Eastern" is used by MCParks today. */
    public static ZoneId zoneForLabel(String label) {
        if (label == null) return ZoneId.of("America/New_York");
        return switch (label.trim().toLowerCase(Locale.ENGLISH)) {
            case "eastern", "et", "est", "edt" -> ZoneId.of("America/New_York");
            case "central", "ct", "cst", "cdt" -> ZoneId.of("America/Chicago");
            case "mountain", "mt", "mst", "mdt" -> ZoneId.of("America/Denver");
            case "pacific", "pt", "pst", "pdt" -> ZoneId.of("America/Los_Angeles");
            case "utc", "gmt" -> ZoneId.of("UTC");
            default -> {
                try { yield ZoneId.of(label); }
                catch (DateTimeParseException e) { yield ZoneId.of("America/New_York"); }
            }
        };
    }

    /** The next {@code ZonedDateTime} at or after {@code now} matching {@code (dow, localTime)} in {@code zone}. */
    public static ZonedDateTime nextOccurrence(DayOfWeek dow, LocalTime localTime, ZoneId zone, ZonedDateTime now) {
        ZonedDateTime zNow = now.withZoneSameInstant(zone);
        ZonedDateTime candidate = zNow
            .with(TemporalAdjusters.nextOrSame(dow))
            .withHour(localTime.getHour())
            .withMinute(localTime.getMinute())
            .withSecond(0)
            .withNano(0);
        if (!candidate.isAfter(zNow)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }

    /** The most recent {@code ZonedDateTime} strictly before {@code now} matching {@code (dow, localTime)} in {@code zone}. */
    public static ZonedDateTime previousOccurrence(DayOfWeek dow, LocalTime localTime, ZoneId zone, ZonedDateTime now) {
        ZonedDateTime zNow = now.withZoneSameInstant(zone);
        ZonedDateTime candidate = zNow
            .with(TemporalAdjusters.previousOrSame(dow))
            .withHour(localTime.getHour())
            .withMinute(localTime.getMinute())
            .withSecond(0)
            .withNano(0);
        if (!candidate.isBefore(zNow)) {
            candidate = candidate.minusWeeks(1);
        }
        return candidate;
    }

    /**
     * Builds a sorted list of every scheduled show from the grid, with its next
     * absolute occurrence. Shows whose row or column headers couldn't be parsed
     * are skipped silently.
     */
    public static List<UpcomingShow> upcoming(ShowTimesGrid grid, ZonedDateTime now) {
        List<UpcomingShow> out = new ArrayList<>();
        for (ShowTimesGrid.Cell cell : grid.cells()) {
            Optional<DayOfWeek> dow = parseDayLabel(grid.dayLabel(cell.row()));
            Optional<ParsedHeader> hdr = parseTimeHeader(grid.timeHeader(cell.col()));
            if (dow.isEmpty() || hdr.isEmpty()) continue;
            ZonedDateTime when = nextOccurrence(dow.get(), hdr.get().time(), hdr.get().zone(), now);
            out.add(new UpcomingShow(cell.showName(), when, hdr.get(), dow.get()));
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Builds a list of past occurrences for every cell in the grid, sorted most-recent first.
     * Use {@link #upcoming} for the future side; this is the symmetric "what just happened"
     * view used by the overlay's left panel.
     */
    public static List<UpcomingShow> recent(ShowTimesGrid grid, ZonedDateTime now) {
        List<UpcomingShow> out = new ArrayList<>();
        for (ShowTimesGrid.Cell cell : grid.cells()) {
            Optional<DayOfWeek> dow = parseDayLabel(grid.dayLabel(cell.row()));
            Optional<ParsedHeader> hdr = parseTimeHeader(grid.timeHeader(cell.col()));
            if (dow.isEmpty() || hdr.isEmpty()) continue;
            ZonedDateTime when = previousOccurrence(dow.get(), hdr.get().time(), hdr.get().zone(), now);
            out.add(new UpcomingShow(cell.showName(), when, hdr.get(), dow.get()));
        }
        out.sort(Comparator.reverseOrder());
        return out;
    }

    /** Formats a local-time conversion suffix for a time-header tooltip line. */
    public static String formatLocalSuffix(ParsedHeader header, ZoneId userZone, ZonedDateTime now) {
        if (userZone.equals(header.zone())) return "";
        ZonedDateTime today = nextOccurrence(now.getDayOfWeek(), header.time(), header.zone(), now.minusDays(1));
        ZonedDateTime local = today.withZoneSameInstant(userZone);
        String abbr = local.format(DateTimeFormatter.ofPattern("z", Locale.ENGLISH));
        return DISPLAY_12H.format(local) + " " + abbr;
    }

    /**
     * Like {@link #formatCountdown} but always carries the seconds component,
     * so a panel rendered every frame visibly ticks down. Used for the "NEXT"
     * countdown where second-level precision is informative.
     */
    public static String formatCountdownPrecise(Duration d) {
        if (d.isNegative() || d.isZero()) return "now";
        long total = d.getSeconds();
        long days = total / 86400; total %= 86400;
        long hours = total / 3600; total %= 3600;
        long minutes = total / 60; long seconds = total % 60;
        StringBuilder sb = new StringBuilder("in ");
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString();
    }

    /** Short human countdown, e.g. {@code "in 2h 14m"} / {@code "in 45s"}. */
    public static String formatCountdown(Duration d) {
        if (d.isNegative() || d.isZero()) return "now";
        long total = d.getSeconds();
        long days = total / 86400; total %= 86400;
        long hours = total / 3600; total %= 3600;
        long minutes = total / 60; long seconds = total % 60;
        StringBuilder sb = new StringBuilder("in ");
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || (days == 0 && hours == 0)) {
            if (days == 0 && hours == 0 && minutes == 0) {
                sb.append(seconds).append("s");
            } else {
                sb.append(minutes).append("m");
            }
        }
        return sb.toString().trim();
    }

    /** Short human "time since" string, e.g. {@code "2h 14m ago"} / {@code "45s ago"}. */
    public static String formatTimeAgo(Duration d) {
        if (d.isNegative()) d = d.negated();
        if (d.isZero()) return "just now";
        long total = d.getSeconds();
        long days = total / 86400; total %= 86400;
        long hours = total / 3600; total %= 3600;
        long minutes = total / 60; long seconds = total % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || (days == 0 && hours == 0)) {
            if (days == 0 && hours == 0 && minutes == 0) {
                sb.append(seconds).append("s");
            } else {
                sb.append(minutes).append("m");
            }
        }
        return sb.toString().trim() + " ago";
    }

    public record ParsedHeader(LocalTime time, String label, ZoneId zone) {}

    public record UpcomingShow(
        String name,
        ZonedDateTime whenServerZone,
        ParsedHeader header,
        DayOfWeek dayOfWeek
    ) implements Comparable<UpcomingShow> {
        @Override
        public int compareTo(UpcomingShow o) {
            return this.whenServerZone.compareTo(o.whenServerZone);
        }
    }
}
