package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Disneyland Railroad &mdash; the ~15-minute grand circle tour around the park,
 * stopping at Main Street, New Orleans Square, Mickey's Toontown, and
 * Tomorrowland, with a narrated trip through the Grand Canyon / Primeval World.
 *
 * <p>Detection: player is a passenger in "Disneyland Resort" and the last
 * teleport announcement landed them at one of the four station ride-ids. This
 * is deliberately loose &mdash; we don't have the train car's item/damage
 * pinned down yet, and the Narrator chat lines are what we actually want to
 * subtitle.
 *
 * <p>Subtitles: strip the {@code "[Narrator] "} prefix MCParks uses for train
 * announcements and feed the rest to {@code SubtitleManager}. Other Disneyland
 * attractions (e.g. Great Moments with Mr. Lincoln) also use the Narrator
 * prefix, so park/rideId gating is what keeps this from firing in the theater.
 */
public class DisneylandRailroad implements RideExperience {

    private static final String NAME = "Disneyland Railroad";
    private static final String PARK = "Disneyland Resort";
    private static final String NARRATOR_PREFIX = "[Narrator] ";

    // Ride ids seen in the MCParks "Traveling to <id> in <park>" chat message
    // when boarding the railroad at any of its four stations.
    private static final String[] STATION_RIDE_IDS = {
        "MainStreetStation",
        "NewOrleansSquareStation",
        "ToontownDepot",
        "TomorrowlandStation"
    };

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }

    /** Grand circle tour runs ~15 minutes; used for HUD progress only. */
    @Override public int rideTimeSeconds() { return 15 * 60; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!ctx.isPassenger) return false;
        if (!PARK.equals(ctx.currentPark)) return false;
        return ctx.rideIdMatchesAny(STATION_RIDE_IDS);
    }

    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();
        if (!text.startsWith(NARRATOR_PREFIX)) return Optional.empty();
        String body = text.substring(NARRATOR_PREFIX.length()).trim();
        return body.isEmpty() ? Optional.empty() : Optional.of(body);
    }
}
