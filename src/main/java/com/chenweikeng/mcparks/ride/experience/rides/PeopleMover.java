package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Audio tracks that indicate the rider is standing on the boarding
     * platform (not yet a passenger). While any of these is playing the
     * ride is treated as "active" so the boarding-loop subtitles display.
     */
    private static final Set<String> PRESHOW_TRACKS = Set.of(
            "MK/Tomorrowland/TTA/Boardingloop"
    );

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
        if (!PARK.equals(ctx.currentPark)) return false;
        if (ctx.isPassenger) {
            for (ExperienceContext.NearbyModel m : ctx.nearbyModels) {
                if (m.matches(VEHICLE_ITEM, VEHICLE_DAMAGE)) return true;
            }
            return false;
        }
        // Preshow: activate when the boarding-loop audio is playing so the
        // platform subtitles display before the player boards a car.
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && PRESHOW_TRACKS.contains(t.name())) return true;
        }
        return false;
    }

    /** No narration observed on this ride. */
    @Override
    public Optional<String> captureSubtitle(Component message) {
        return Optional.empty();
    }
}
