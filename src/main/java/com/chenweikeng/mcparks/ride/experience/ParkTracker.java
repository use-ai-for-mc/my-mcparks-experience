package com.chenweikeng.mcparks.ride.experience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the player's current park and ride from the sidebar scoreboard.
 *
 * <p>The sidebar typically contains two useful entries:
 * <ul>
 *   <li>{@code "Park: <code>"} &mdash; the short park code (e.g. {@code "WDW"},
 *       or empty for Disneyland Resort). Mapped to a full name via
 *       {@link #PARK_CODE_MAP}.</li>
 *   <li>{@code "Current Ride"} label &mdash; the next entry below it holds the
 *       ride's display name (e.g. {@code "Splash Mountain"},
 *       {@code "Haunted Mansion"}).</li>
 * </ul>
 *
 * <p>Both are read every time {@link #tryReadFromScoreboard} is called (once
 * per {@link ExperienceContext#current()} build). When the park code changes
 * the stale ride name is cleared automatically.
 */
public final class ParkTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksParkTracker");

    /**
     * Maps the short park code shown in the sidebar to the full park name.
     * Disneyland Resort leaves the code empty ({@code "Park: "} with no
     * suffix), so the empty-string key maps to it.
     */
    private static final Map<String, String> PARK_CODE_MAP = Map.ofEntries(
        Map.entry("",     "Disneyland Resort"),
        Map.entry("WDW",  "Walt Disney World Resort"),
        Map.entry("DL",   "Disneyland Resort"),
        Map.entry("UOR",  "Universal Orlando Resort"),
        Map.entry("DLP",  "Disneyland Paris"),
        Map.entry("USH",  "Universal Studios Hollywood"),
        Map.entry("TDR",  "Tokyo Disney Resort")
    );

    private static final String PARK_PREFIX = "Park: ";
    private static final String CURRENT_RIDE_LABEL = "Current Ride";

    private static final ParkTracker INSTANCE = new ParkTracker();

    public static ParkTracker getInstance() {
        return INSTANCE;
    }

    private volatile String currentPark;
    /** Display name of the current ride read from the scoreboard, e.g. "Haunted Mansion". */
    private volatile String currentRideName;
    /** Raw sidebar code, e.g. "WDW". Stored for change-detection and diagnostics. */
    private volatile String currentParkCode;
    /** True once we've read the scoreboard at least once this session. */
    private volatile boolean scoreboardRead;

    private ParkTracker() {}

    public String currentPark() { return currentPark; }
    public String currentRideName() { return currentRideName; }
    public String currentParkCode() { return currentParkCode; }

    // ---- Scoreboard-based detection ----

    /**
     * Read park and ride name from the sidebar scoreboard. Called every time
     * {@link ExperienceContext#current()} builds a snapshot. Walks the sidebar
     * entries looking for {@code "Park: <code>"} and the
     * {@code "Current Ride"} label (whose next entry is the ride display name).
     */
    public void tryReadFromScoreboard(Minecraft mc) {
        if (mc.level == null) return;

        try {
            Scoreboard sb = mc.level.getScoreboard();
            Objective sidebar = sb.getDisplayObjective(1); // slot 1 = sidebar
            if (sidebar == null) return;

            // Collect entries sorted by score descending (sidebar display order)
            List<ScoredEntry> entries = new ArrayList<>();
            for (Score score : sb.getPlayerScores(sidebar)) {
                PlayerTeam team = sb.getPlayersTeam(score.getOwner());
                if (team == null) continue;
                String display = team.getPlayerPrefix().getString()
                               + team.getPlayerSuffix().getString();
                entries.add(new ScoredEntry(score.getScore(), display));
            }
            entries.sort(Comparator.comparingInt((ScoredEntry e) -> e.score).reversed());

            String newParkCode = null;
            String newRideName = null;

            for (int i = 0; i < entries.size(); i++) {
                String display = stripDisplayPrefix(entries.get(i).display);

                // Park code: "Park: WDW" or "Park: " (empty = DL)
                if (display.startsWith(PARK_PREFIX)) {
                    newParkCode = display.substring(PARK_PREFIX.length()).trim();
                }

                // "Current Ride" label → next non-blank entry below is the ride name
                if (CURRENT_RIDE_LABEL.equals(display) && i + 1 < entries.size()) {
                    String next = stripDisplayPrefix(entries.get(i + 1).display);
                    if (!next.isEmpty()) {
                        newRideName = next;
                    }
                }
            }

            // ---- Update park if code resolved and changed ----
            if (newParkCode != null) {
                String fullName = PARK_CODE_MAP.get(newParkCode);
                if (fullName != null) {
                    if (!newParkCode.equals(currentParkCode) || !fullName.equals(currentPark)) {
                        LOGGER.info("Park context (scoreboard): code='{}' -> '{}'"
                            + (currentPark != null ? " (was '{}')" : ""),
                            newParkCode, fullName, currentPark);
                        currentParkCode = newParkCode;
                        currentPark = fullName;
                    }
                } else if (!newParkCode.equals(currentParkCode)) {
                    LOGGER.warn("Unknown park code from scoreboard: '{}'. "
                        + "Add it to PARK_CODE_MAP.", newParkCode);
                    currentParkCode = newParkCode;
                }
            }

            // ---- Update ride name if changed ----
            if (newRideName != null && !newRideName.equals(currentRideName)) {
                LOGGER.info("Ride context (scoreboard): '{}'"
                    + (currentRideName != null ? " (was '{}')" : ""),
                    newRideName, currentRideName);
                currentRideName = newRideName;
            } else if (newRideName == null && currentRideName != null) {
                LOGGER.info("Ride context (scoreboard): cleared (was '{}')", currentRideName);
                currentRideName = null;
            }

            scoreboardRead = true;
        } catch (Exception e) {
            LOGGER.debug("Failed to read from scoreboard", e);
        }
    }

    /**
     * Strip the leading {@code " | "} prefix some servers use in sidebar
     * display text. Returns the cleaned display string.
     */
    private static String stripDisplayPrefix(String display) {
        display = display.trim();
        if (display.startsWith("| ")) {
            display = display.substring(2).trim();
        }
        return display;
    }

    /** Reset on disconnect. */
    public void reset() {
        currentPark = null;
        currentRideName = null;
        currentParkCode = null;
        scoreboardRead = false;
    }

    private record ScoredEntry(int score, String display) {}
}
