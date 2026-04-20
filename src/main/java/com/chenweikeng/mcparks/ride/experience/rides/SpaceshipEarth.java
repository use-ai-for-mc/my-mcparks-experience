package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;

/**
 * Spaceship Earth &mdash; the EPCOT geodesic-sphere dark ride narrated by
 * Judi Dench (current MCParks build uses the current-gen narration).
 *
 * <p>Vehicle: invisible armor stand with {@code iron_axe:47}
 * ({@code vehicles/wdw/spaceship_earth} in the resource pack, per
 * {@code /nearby}).
 *
 * <p>Timed subtitles cover the 20 {@code sse/script/*} narration tracks
 * plus the default {@code sse/future/welcomeToFuture}, {@code theEnd},
 * and the {@code sse/future/leisure/*} descent personalization set (the
 * "travel" category captured across 3 observation rides). Other descent
 * categories (e.g. work, home) play different {@code sse/future/<cat>/*}
 * clips and are not yet captured &mdash; they'll be added as sessions
 * surface them.
 */
public class SpaceshipEarth implements RideExperience {

    private static final String NAME = "Spaceship Earth";
    private static final String PARK = "Walt Disney World Resort";

    /** Omnimover: iron_axe with damage 47. */
    private static final String VEHICLE_ITEM = "iron_axe";
    private static final int VEHICLE_DAMAGE = 47;

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/spaceship_earth.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }

    /** Full cycle observed in session log: 16 min 39 sec. */
    @Override public int rideTimeSeconds() { return 16 * 60 + 39; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!ctx.isPassenger) return false;
        // No park gate — SSE's vehicle (iron_axe:47) is unique across the
        // server, and EPCOT scoreboards leave the park code blank (same
        // situation LWTL documents). MCParks EPCOT also doesn't publish a
        // ride name for SSE, so match purely on the vehicle marker.
        for (ExperienceContext.NearbyModel m : ctx.nearbyModels) {
            if (m.matches(VEHICLE_ITEM, VEHICLE_DAMAGE)) return true;
        }
        return false;
    }
}
