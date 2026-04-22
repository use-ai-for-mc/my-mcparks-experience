package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * Journey Into Imagination With Figment &mdash; the whimsical dark ride
 * through the Imagination Institute at EPCOT (WDW). Detected via the
 * scoreboard ride name {@code "Figment"} or the vehicle model
 * ({@code iron_axe:21}).
 *
 * <p>Vehicle: invisible armor stand with {@code iron_axe:21}
 * ({@code vehicles/jii} in the resource pack).
 *
 * <p>Speaker prefixes observed in chat (sibling structure, not root color):
 * <ul>
 *   <li>{@code [Figment]} &mdash; name in dark purple ({@code 0xAA00AA}, &sect;5),
 *       dialogue in light purple ({@code 0xFF55FF}, &sect;d)
 *   <li>{@code [Dr. Channing]} &mdash; name in dark aqua ({@code 0x00AAAA}, &sect;3),
 *       dialogue in aqua ({@code 0x55FFFF}, &sect;b)
 * </ul>
 *
 * <p>Brackets ({@code [ ]}) are gray ({@code 0xAAAAAA}, &sect;7) for both
 * speakers. No italic or bold styling is used.
 *
 * <p>Detection and subtitles are gated on {@code isPassenger}.
 */
public class JourneyIntoImagination implements RideExperience {

    private static final String NAME = "Journey Into Imagination With Figment";
    private static final String PARK = "Walt Disney World Resort";

    /** Ride vehicle: iron_axe with damage 21. */
    private static final String VEHICLE_ITEM = "iron_axe";
    private static final int VEHICLE_DAMAGE = 21;

    /** Named speaker prefixes, checked in order. */
    private static final String[] SPEAKER_PREFIXES = {
        "[Figment] ",
        "[Dr. Channing] "
    };

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }

    /** Full ride cycle: 7 min 32 sec. */
    @Override public int rideTimeSeconds() { return 7 * 60 + 32; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!ctx.isPassenger) return false;
        if (!PARK.equals(ctx.currentPark)) return false;
        for (ExperienceContext.NearbyModel m : ctx.nearbyModels) {
            if (m.matches(VEHICLE_ITEM, VEHICLE_DAMAGE)) return true;
        }
        return false;
    }

    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();

        // Named speaker prefixes — strip prefix, subtitle the body
        for (String prefix : SPEAKER_PREFIXES) {
            if (text.startsWith(prefix)) {
                String body = text.substring(prefix.length()).trim();
                return body.isEmpty() ? Optional.empty() : Optional.of(body);
            }
        }

        return Optional.empty();
    }
}
