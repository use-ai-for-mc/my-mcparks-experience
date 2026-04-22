package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Set;

/**
 * Library pre-show for {@link TowerOfTerror} &mdash; the ~1:43 Rod
 * Serling introduction that plays in the queue's library room before
 * guests walk to the boiler-room load area and board the elevator.
 *
 * <p>Split out from {@link TowerOfTerror} so it doesn't pollute the
 * main ride's timeline: the time between preshow end and actual
 * boarding is player-controlled (guests walk at their own pace, and
 * some guests experience the preshow but never board), so treating
 * the preshow as part of the ride's {@code totalRideTimeSec} produces
 * a meaningless HUD countdown. This sibling class exists purely for
 * its timed subtitles.
 *
 * <p>Detection fires whenever the {@code TowerOfTerror/TOTPreshow}
 * audio track is playing in the WDW park &mdash; no passenger or
 * vehicle gate. Once the track finishes, {@link #isActive} returns
 * false and the experience deactivates; if the guest then boards the
 * elevator, {@link TowerOfTerror} takes over with its own ride-proper
 * timeline starting at {@code t=0}.
 *
 * <p>HUD timer stays hidden ({@link #rideTimeSeconds} returns
 * {@code -1}) &mdash; the preshow isn't a "ride" for HUD purposes, and
 * {@link com.chenweikeng.mcparks.ride.RideDetector#isOnRide
 * RideDetector#isOnRide} additionally requires {@code wasPassenger}
 * to be true, so the HUD couldn't render here anyway.
 */
public class TowerOfTerrorPreshow implements RideExperience {

    private static final String NAME = "The Twilight Zone Tower of Terror (Preshow)";
    private static final String PARK = "Walt Disney World Resort";

    /** The library-scene pre-show track. Oneshot, ~103 s long. */
    private static final Set<String> PRESHOW_TRACKS = Set.of(
            "TowerOfTerror/TOTPreshow"
    );

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/tot_preshow.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }

    /** No HUD countdown for the preshow. */
    @Override public int rideTimeSeconds() { return -1; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && PRESHOW_TRACKS.contains(t.name())) return true;
        }
        return false;
    }
}
