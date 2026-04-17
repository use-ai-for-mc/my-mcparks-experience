package com.chenweikeng.mcparks.ride.experience;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the player's current MCParks park + ride id.
 *
 * <h3>Two data sources, in priority order:</h3>
 * <ol>
 *   <li><b>Chat:</b> the server-side teleport announcement
 *       {@code "Traveling to <rideId> in <park>"} sets both park and ride id
 *       authoritatively.</li>
 *   <li><b>Scoreboard sidebar:</b> MCParks always shows
 *       {@code "Park: <code>"} in the sidebar (e.g. {@code "Park: WDW"}).
 *       This is available from the moment the player joins, so it serves as
 *       the fallback when no chat message has been seen yet. We map the short
 *       code to the full park name.</li>
 * </ol>
 *
 * <p>MCParks runs all parks in {@code minecraft:overworld} on 1.19, so
 * dimensions are useless as a park discriminator.
 */
public final class ParkTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksParkTracker");
    private static final Pattern TRAVELING_RE =
        Pattern.compile("^Traveling to (.+?) in (.+?)\\s*$");

    /**
     * Maps the short park code shown in the sidebar (e.g. "WDW") to the full
     * park name used in {@code "Traveling to X in Y"} announcements. Extend
     * this map as we encounter new parks in the logs.
     *
     * <p>Disneyland Resort leaves the code <em>empty</em> on the sidebar
     * ({@code "Park: "} with no suffix), so the empty-string key maps to it.
     */
    private static final Map<String, String> PARK_CODE_MAP = Map.ofEntries(
        Map.entry("",     "Disneyland Resort"),   // DL shows "Park: " with no code
        Map.entry("WDW",  "Walt Disney World Resort"),
        Map.entry("DL",   "Disneyland Resort"),   // in case they add it later
        Map.entry("UOR",  "Universal Orlando Resort"),
        Map.entry("DLP",  "Disneyland Paris"),
        Map.entry("USH",  "Universal Studios Hollywood"),
        Map.entry("TDR",  "Tokyo Disney Resort")
    );

    private static final String PARK_PREFIX = "Park: ";

    private static final ParkTracker INSTANCE = new ParkTracker();

    public static ParkTracker getInstance() {
        return INSTANCE;
    }

    private volatile String currentPark;
    private volatile String currentRideId;
    /** Raw sidebar code, e.g. "WDW". Stored for diagnostics. */
    private volatile String currentParkCode;
    /** True once we've read the scoreboard at least once this session. */
    private volatile boolean scoreboardRead;

    private ParkTracker() {}

    public String currentPark() { return currentPark; }
    public String currentRideId() { return currentRideId; }
    public String currentParkCode() { return currentParkCode; }

    // ---- Chat-based tracking (authoritative) ----

    /** Call with every incoming chat message. Returns true if this was a travel message. */
    public boolean observe(Component message) {
        String text = message.getString();
        if (!text.startsWith("Traveling to ")) return false;
        Matcher m = TRAVELING_RE.matcher(text);
        if (!m.matches()) return false;
        String rideId = m.group(1).trim();
        String park = m.group(2).trim();
        currentRideId = rideId;
        currentPark = park;
        LOGGER.info("Park context (chat): park='{}' rideId='{}'", park, rideId);
        return true;
    }

    // ---- Scoreboard-based detection (fallback on initial join) ----

    /**
     * Try to read the park from the MCParks sidebar scoreboard. Called lazily
     * from {@link ExperienceContext#current()} when {@link #currentPark} is
     * {@code null}. Reads the sidebar objective, walks its score entries,
     * decodes each entry's team prefix+suffix, and looks for
     * {@code "Park: <code>"}.
     *
     * <p>Only sets {@code currentPark} if it hasn't been set by a chat message
     * already, since the chat source is more authoritative (it also gives us
     * the rideId).
     */
    public void tryReadFromScoreboard(Minecraft mc) {
        if (currentPark != null) return;      // already known
        if (mc.level == null) return;

        try {
            Scoreboard sb = mc.level.getScoreboard();
            Objective sidebar = sb.getDisplayObjective(1); // slot 1 = sidebar
            if (sidebar == null) return;

            for (Score score : sb.getPlayerScores(sidebar)) {
                PlayerTeam team = sb.getPlayersTeam(score.getOwner());
                if (team == null) continue;
                String display = team.getPlayerPrefix().getString()
                               + team.getPlayerSuffix().getString();
                if (!display.startsWith(PARK_PREFIX)) continue;

                String code = display.substring(PARK_PREFIX.length()).trim();
                currentParkCode = code;
                String fullName = PARK_CODE_MAP.get(code);
                if (fullName != null) {
                    currentPark = fullName;
                    LOGGER.info("Park context (scoreboard): code='{}' -> '{}'", code, fullName);
                } else {
                    LOGGER.warn("Unknown park code from scoreboard: '{}'. "
                        + "Add it to PARK_CODE_MAP in ParkTracker.", code);
                }
                scoreboardRead = true;
                return;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to read park from scoreboard", e);
        }
    }

    /** Reset on disconnect. */
    public void reset() {
        currentPark = null;
        currentRideId = null;
        currentParkCode = null;
        scoreboardRead = false;
    }
}
