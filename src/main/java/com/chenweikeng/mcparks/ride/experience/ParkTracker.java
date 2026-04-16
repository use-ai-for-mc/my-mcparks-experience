package com.chenweikeng.mcparks.ride.experience;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the player's current MCParks park + ride id by watching for the
 * server-side teleport announcement: {@code "Traveling to <rideId> in <park>"}.
 *
 * <p>Examples from the MCParks chat:
 * <pre>
 *   Traveling to MainStreetStation in Disneyland Resort
 *   Traveling to Lincoln in Disneyland Resort
 *   Traveling to hm in Walt Disney World Resort
 *   Traveling to spiderman in Universal Orlando Resort
 * </pre>
 *
 * <p>We use this as the "world name" filter for {@link RideExperience}
 * detection &mdash; Minecraft dimensions aren't reliable for this because
 * MCParks runs multiple parks in the same dimension.
 */
public final class ParkTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksParkTracker");
    private static final Pattern TRAVELING_RE =
        Pattern.compile("^Traveling to (.+?) in (.+?)\\s*$");

    private static final ParkTracker INSTANCE = new ParkTracker();

    public static ParkTracker getInstance() {
        return INSTANCE;
    }

    private volatile String currentPark;
    private volatile String currentRideId;

    private ParkTracker() {}

    public String currentPark() { return currentPark; }
    public String currentRideId() { return currentRideId; }

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
        LOGGER.info("Park context: park='{}' rideId='{}'", park, rideId);
        return true;
    }

    /** Reset on disconnect. */
    public void reset() {
        currentPark = null;
        currentRideId = null;
    }
}
