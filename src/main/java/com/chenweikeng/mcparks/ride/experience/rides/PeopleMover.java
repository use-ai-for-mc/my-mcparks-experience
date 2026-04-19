package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Tomorrowland Transit Authority PeopleMover &mdash; the elevated
 * tour of Tomorrowland at Magic Kingdom (WDW). Detected via the
 * scoreboard ride name {@code "TTA"} or the vehicle model
 * ({@code iron_axe:22}).
 *
 * <p>Vehicle: invisible armor stand with {@code iron_axe:22}
 * ({@code vehicles/tta} in the resource pack).
 *
 * <p>No chat-based narration or speaker prefixes have been observed
 * on this ride &mdash; {@link #captureSubtitle} always returns empty.
 *
 * <p>Detection and the HUD ride-time counter are gated on
 * {@code isPassenger}.
 */
public class PeopleMover implements RideExperience {

    private static final String NAME = "Tomorrowland Transit Authority PeopleMover";
    private static final String PARK = "Walt Disney World Resort";

    /** PeopleMover car: iron_axe with damage 22. */
    private static final String VEHICLE_ITEM = "iron_axe";
    private static final int VEHICLE_DAMAGE = 22;

    /** Timed subtitle data parsed from .ass files for the boarding loop plus 18 narration tracks. */
    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/peoplemover.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }

    /** Full loop: 8 min 11 sec. */
    @Override public int rideTimeSeconds() { return 8 * 60 + 11; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!ctx.isPassenger) return false;
        if (!PARK.equals(ctx.currentPark)) return false;
        // Primary: ride name from sidebar scoreboard
        if (ctx.rideNameMatchesAny("TTA", "PeopleMover",
                "Tomorrowland Transit Authority PeopleMover")) return true;
        // Fallback: player is riding the PeopleMover vehicle
        for (ExperienceContext.NearbyModel m : ctx.nearbyModels) {
            if (m.matches(VEHICLE_ITEM, VEHICLE_DAMAGE)) return true;
        }
        return false;
    }

    /** No narration observed on this ride. */
    @Override
    public Optional<String> captureSubtitle(Component message) {
        return Optional.empty();
    }
}
