package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Haunted Mansion &mdash; 999 happy haunts, with room for one more. The
 * Magic Kingdom (WDW) variant; MCParks announces it as
 * {@code "Traveling to hm in Walt Disney World Resort"}.
 *
 * <p>Detection is deliberately <em>not</em> gated on {@code isPassenger}: the
 * Ghost Host narration starts in the pre-show stretching room before the
 * guest boards a Doom Buggy, and we want those lines subtitled too. Once the
 * player does board a Doom Buggy, {@code scanForRideModels} finds this
 * experience active and the HUD ride-time counter kicks in at that moment.
 *
 * <p>If we later want to distinguish between the WDW / Disneyland / DLP / Tokyo
 * variants (different dialogue tracks), add sibling classes with park-specific
 * {@link #park()} values.
 */
public class HauntedMansion implements RideExperience {

    private static final String NAME = "Haunted Mansion";
    private static final String PARK = "Walt Disney World Resort";
    private static final String RIDE_ID = "hm";
    private static final String GHOST_HOST_PREFIX = "[Ghost Host] ";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }

    /** Full doom-buggy cycle is roughly 8 minutes. */
    @Override public int rideTimeSeconds() { return 8 * 60; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        return RIDE_ID.equalsIgnoreCase(ctx.currentRideId);
    }

    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();
        if (!text.startsWith(GHOST_HOST_PREFIX)) return Optional.empty();
        String body = text.substring(GHOST_HOST_PREFIX.length()).trim();
        return body.isEmpty() ? Optional.empty() : Optional.of(body);
    }
}
