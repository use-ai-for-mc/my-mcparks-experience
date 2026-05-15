package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.firework.FireworkLocations;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Set;

/**
 * Pre-show countdown for {@link HappilyEverAfter}. Two announcement
 * tracks fire in the Magic Kingdom hub before showtime:
 * <ul>
 *   <li>{@code HEA/Preshow10Min} &mdash; T&minus;10:00 announcement
 *       (5-min track that hard-stops at T&minus;5:00).</li>
 *   <li>{@code HEA/Preshow5Min} &mdash; T&minus;5:00 announcement
 *       (5-min track that hard-stops at T&minus;0:00).</li>
 * </ul>
 *
 * <p>The HUD timer is anchored to the start of the 10-minute track and
 * counts down toward HEA/1SFX (which fires at T&plus;15:16 in observed
 * data &mdash; preshow ends at T&plus;10:00, then a ~5-minute narration
 * gap). The experience deactivates at T&plus;10:00 because both preshow
 * tracks have stopped; the main {@link HappilyEverAfter} experience picks
 * up at T&plus;15:16. The 5-minute gap is the {@code MK/Shows/SpecialOpening}
 * narration window, which is shared with non-HEA castle shows and
 * therefore not gated here.
 *
 * <p>Detection requires both:
 * <ol>
 *   <li>One of the two preshow tracks is currently playing (HEA-namespace
 *       gives strong specificity), and</li>
 *   <li>The player is at the Magic Kingdom castle viewing spot
 *       (Walt-statue check), confirming geo-location.</li>
 * </ol>
 *
 * <p>Subtitles are in
 * {@code assets/my-mcparks-experience/subtitles/hea_preshow.json},
 * anchored to {@code HEA/Preshow10Min} = T+0.
 */
public class HappilyEverAfterPreshow implements RideExperience {

    private static final String NAME = "Happily Ever After (Preshow)";
    private static final String PARK = "Walt Disney World Resort";

    private static final Set<String> PRESHOW_TRACKS = Set.of(
            "HEA/Preshow10Min",
            "HEA/Preshow5Min"
    );

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/hea_preshow.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }
    @Override public boolean isTheaterMode() { return true; }

    /**
     * Countdown total: from {@code HEA/Preshow10Min} start to
     * {@code HEA/1SFX} start = 916&nbsp;s (15:16). The HUD will read this
     * from the JSON's {@code totalRideTimeSec} once subtitles load; the
     * value here is the fallback before that.
     */
    @Override public int rideTimeSeconds() { return 916; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        if (!hasPreshowAudio()) return false;
        return FireworkLocations.MAGIC_KINGDOM.isHereNow(ctx.mc);
    }

    private static boolean hasPreshowAudio() {
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && PRESHOW_TRACKS.contains(t.name())) return true;
        }
        return false;
    }
}
