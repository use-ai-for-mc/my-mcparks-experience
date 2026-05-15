package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.firework.FireworkLocations;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Set;

/**
 * Happily Ever After &mdash; the Walt Disney World Magic Kingdom castle
 * fireworks show in front of Cinderella Castle. Theater-style: guests stand
 * in the hub area watching the castle, no boarding event.
 *
 * <p><b>Detection.</b> Activates while any of the seven HEA SFX tracks
 * (HEA/1SFX&hellip;HEA/7SFX) is playing AND the player is at the Magic
 * Kingdom castle viewing spot (Walt-statue check). HEA has no variants and
 * the SFX always run strictly 1&rarr;7 with sub-second handoffs, so any one
 * SFX active keeps the gate hot. The location AND-condition guards against
 * the unlikely case of HEA audio being played outside the geo-fenced
 * castle hub.
 *
 * <p>Excluded from the gate (deliberate):
 * <ul>
 *   <li>{@code HEA/Preshow10Min} / {@code HEA/Preshow5Min} &mdash; covered
 *       by the sibling {@link HappilyEverAfterPreshow} class.</li>
 *   <li>{@code MK/Shows/SpecialOpening} and the
 *       {@code DarrenNeutral}/{@code RyanNeutral} narrators &mdash; shared
 *       across nighttime castle shows (Wishes, Enchantment, HEA), so they
 *       cannot identify HEA specifically.</li>
 *   <li>{@code Anniversary/MomentousTag} and
 *       {@code Anniversary/CelebrateYou} &mdash; closing tags that always
 *       follow HEA but are event-tied and play at non-HEA contexts too.</li>
 * </ul>
 *
 * <p>Subtitle data is in
 * {@code assets/my-mcparks-experience/subtitles/hea.json}, anchored to
 * {@code HEA/1SFX} = T+0.
 *
 * <p>TODO: verify {@code HEA/2SFX} duration (~4m12s tonight) is consistent
 * across shows or whether the showrunner paces it loosely &mdash; affects
 * subtitle cue placement once authored.
 *
 * <p>TODO: confirm whether the {@code DarrenNeutral} cue is normally cut
 * after &lt;10 s or whether tonight's truncation was a misfire &mdash;
 * doesn't affect this gate, but matters for any future narration phase.
 */
public class HappilyEverAfter implements RideExperience {

    private static final String NAME = "Happily Ever After";
    private static final String PARK = "Walt Disney World Resort";

    /**
     * Seven main-show SFX segments. The MCParks server publishes them as
     * one-shot {@code show-0-…} (or {@code show-1-…}, equivalent) frames in
     * order 1&rarr;7 with hard cuts between segments.
     */
    private static final Set<String> SHOW_TRACKS = Set.of(
            "HEA/1SFX",
            "HEA/2SFX",
            "HEA/3SFX",
            "HEA/4SFX",
            "HEA/5SFX",
            "HEA/6SFX",
            "HEA/7SFX"
    );

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/hea.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }
    @Override public boolean isTheaterMode() { return true; }

    /**
     * Main pyrotechnic show (HEA/1SFX through HEA/7SFX) ran 1102&nbsp;s in
     * the 2026-04-25 capture. Server-stopped per segment, so individual
     * segment lengths can drift between shows; total runtime is fairly
     * stable.
     */
    @Override public int rideTimeSeconds() { return 1102; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        if (!hasShowAudio()) return false;
        return FireworkLocations.MAGIC_KINGDOM.isHereNow(ctx.mc);
    }

    private static boolean hasShowAudio() {
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && SHOW_TRACKS.contains(t.name())) return true;
        }
        return false;
    }
}
