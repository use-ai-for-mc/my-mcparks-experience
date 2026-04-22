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
 * <p><b>Currently disabled.</b> The earlier detection gate depended on a
 * {@code "Current Ride"} sidebar scoreboard entry that MCParks does not
 * publish, so {@link #isActive} has been returning {@code false} since
 * inception. Leave the class in place and unregister it until a first-class
 * detection signal is available. Likely paths:
 * <ul>
 *   <li>Vehicle marker &mdash; the resource pack defines
 *       {@code diamond_axe:17} (ckholliday_engine),
 *       {@code diamond_axe:18} (ckholliday_passengercar), and
 *       {@code diamond_axe:19} (ckholliday_tender). Observe in-game to
 *       confirm which slot holds the item and whether other DL trains
 *       (Fred Gurley, E.P. Ripley, Ernest S. Marsh) use different damages.</li>
 *   <li>Narration audio track allowlist &mdash; if the DL narration plays on
 *       named audio tracks, gate via {@link com.chenweikeng.mcparks.audio.MCParksAudioService}
 *       the same way {@link WaltDisneyWorldRailroad} does for its depot spiels.</li>
 * </ul>
 *
 * <p>Chat capture is already wired for the {@code [Narrator] } prefix MCParks
 * uses for DL train announcements. Once {@link #isActive} is fixed and the
 * class re-registered, subtitles will work without further changes.
 */
public class DisneylandRailroad implements RideExperience {

    private static final String NAME = "Disneyland Railroad";
    private static final String PARK = "Disneyland Resort";
    private static final String NARRATOR_PREFIX = "[Narrator] ";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }

    /** Grand circle tour runs ~15 minutes; used for HUD progress only. */
    @Override public int rideTimeSeconds() { return 15 * 60; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        // TODO: re-enable once we have vehicle-marker or audio-track detection.
        return false;
    }

    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();
        if (!text.startsWith(NARRATOR_PREFIX)) return Optional.empty();
        String body = text.substring(NARRATOR_PREFIX.length()).trim();
        return body.isEmpty() ? Optional.empty() : Optional.of(body);
    }
}
