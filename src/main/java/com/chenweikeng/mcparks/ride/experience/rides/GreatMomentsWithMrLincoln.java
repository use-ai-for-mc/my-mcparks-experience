package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Great Moments with Mr. Lincoln &mdash; the Audio-Animatronic stage show in
 * the Disneyland Opera House on Main Street USA. MCParks announces it as
 * {@code "Traveling to Lincoln in Disneyland Resort"}.
 *
 * <p>Two speaker prefixes show up in chat: {@code [Narrator]} for the pre-show
 * / framing narration, and {@code [Abraham Lincoln]} for the title figure's
 * lines during the address itself. Both are captured into subtitles.
 *
 * <p>Not gated on {@code isPassenger} &mdash; this is a seated theater show,
 * not a vehicle ride. The {@code [Narrator]} prefix is also used on the
 * Disneyland Railroad, so the {@code rideId == "Lincoln"} check is what keeps
 * this and {@link DisneylandRailroad} from fighting over the same lines.
 *
 * <p>No HUD ride-timer: show runtime varies and there's no vehicle-boarding
 * event to anchor a countdown to.
 */
public class GreatMomentsWithMrLincoln implements RideExperience {

    private static final String NAME = "Great Moments with Mr. Lincoln";
    private static final String PARK = "Disneyland Resort";
    private static final String RIDE_ID = "Lincoln";
    private static final String[] SPEAKER_PREFIXES = {
        "[Narrator] ",
        "[Abraham Lincoln] "
    };

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        return RIDE_ID.equalsIgnoreCase(ctx.currentRideId);
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
