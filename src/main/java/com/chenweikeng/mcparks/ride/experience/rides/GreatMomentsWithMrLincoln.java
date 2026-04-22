package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Great Moments with Mr. Lincoln &mdash; the Audio-Animatronic stage show in
 * the Disneyland Opera House on Main Street USA.
 *
 * <p><b>Currently disabled.</b> The earlier detection gate depended on a
 * {@code "Current Ride"} sidebar scoreboard entry that MCParks does not
 * publish, so {@link #isActive} has been returning {@code false} since
 * inception. As a seated theater show with no vehicle, this ride has no
 * obvious vehicle marker to use as a first-class signal. Re-enable once we
 * have either:
 * <ul>
 *   <li>A distinctive audio track allowlist (e.g. the Lincoln address
 *       recording) &mdash; gate via
 *       {@link com.chenweikeng.mcparks.audio.MCParksAudioService} the same
 *       way {@link PeopleMover} does for its boarding loop.</li>
 *   <li>A location-based check (player inside the Opera House bounding box
 *       in Disneyland Resort).</li>
 * </ul>
 *
 * <p>Chat capture is already wired for the {@code [Narrator] } and
 * {@code [Abraham Lincoln] } speaker prefixes. Once {@link #isActive} is
 * fixed, subtitles will work without further changes.
 *
 * <p>No HUD ride-timer: show runtime varies and there's no vehicle-boarding
 * event to anchor a countdown to.
 */
public class GreatMomentsWithMrLincoln implements RideExperience {

    private static final String NAME = "Great Moments with Mr. Lincoln";
    private static final String PARK = "Disneyland Resort";
    private static final String[] SPEAKER_PREFIXES = {
        "[Narrator] ",
        "[Abraham Lincoln] "
    };

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        // TODO: re-enable once we have audio-track or location-based detection.
        return false;
    }

    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();
        for (String prefix : SPEAKER_PREFIXES) {
            if (text.startsWith(prefix)) {
                String body = text.substring(prefix.length()).trim();
                return body.isEmpty() ? Optional.empty() : Optional.of(body);
            }
        }
        return Optional.empty();
    }
}
