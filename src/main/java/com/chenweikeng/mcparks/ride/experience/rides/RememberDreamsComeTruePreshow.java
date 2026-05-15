package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.firework.FireworkLocations;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;

/**
 * Pre-show countdown for {@link RememberDreamsComeTrue}. Three RDCT-namespaced
 * tracks compose the 10-minute warm-up before the main fireworks begin:
 * <ul>
 *   <li>{@code RDCT/10Minutes} &mdash; T&minus;10:00 announcement (Narrator;
 *       ~1m15s).</li>
 *   <li>{@code RDCT/preshow} &mdash; the looping ambient music bed that runs
 *       before, between, and after the spoken announcements.</li>
 *   <li>{@code RDCT/5Minutes} &mdash; T&minus;5:00 announcement (Narrator;
 *       ~45s).</li>
 *   <li>{@code RDCT/15Minutes} &mdash; defensive: appears in the server's
 *       namespace probe but never observed playing in our capture; included
 *       so a 15-minute warning would still trigger the preshow if it ever
 *       fires.</li>
 * </ul>
 *
 * <p>The HUD timer is anchored to {@code RDCT/10Minutes} = T+0 and counts
 * down toward {@code RDCT/1} (which fires at T+10:00). The experience
 * deactivates the moment all preshow tracks have stopped, which on a normal
 * show is the same instant {@link RememberDreamsComeTrue} activates &mdash;
 * no narration gap (unlike HEA's 5-minute gap between preshow and fireworks).
 *
 * <p>Detection requires both:
 * <ol>
 *   <li>One of the four preshow tracks is currently playing (RDCT-namespace
 *       gives strong specificity), and</li>
 *   <li>The player is at the Disneyland California castle viewing spot
 *       (Walt-statue check), confirming geo-location.</li>
 * </ol>
 *
 * <p>Subtitles are in
 * {@code assets/my-mcparks-experience/subtitles/rdct_preshow.json},
 * anchored to {@code RDCT/10Minutes} = T+0.
 */
public class RememberDreamsComeTruePreshow implements RideExperience {

    private static final String NAME = "Remember… Dreams Come True (Preshow)";
    private static final String PARK = "Disneyland Resort";

    private static final Set<String> PRESHOW_TRACKS = Set.of(
            "RDCT/preshow",
            "RDCT/10Minutes",
            "RDCT/5Minutes",
            "RDCT/15Minutes"
    );

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/rdct_preshow.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }
    @Override public boolean isTheaterMode() { return true; }

    /**
     * Countdown total: from {@code RDCT/10Minutes} start to {@code RDCT/1}
     * start = exactly 600&nbsp;s (10:00). HUD will read this from the JSON's
     * {@code totalRideTimeSec} once subtitles load.
     */
    @Override public int rideTimeSeconds() { return 600; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        if (!hasPreshowAudio()) return false;
        return FireworkLocations.DISNEYLAND_CALIFORNIA.isHereNow(ctx.mc);
    }

    private static boolean hasPreshowAudio() {
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && PRESHOW_TRACKS.contains(t.name())) return true;
        }
        return false;
    }

    /**
     * Suppresses the Narrator's chat-published preshow announcements so the
     * timed subtitle player can drive the on-screen display.
     */
    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();
        if (text.startsWith("[")) {
            int close = text.indexOf("] ");
            if (close > 0) {
                String body = text.substring(close + 2).trim();
                return body.isEmpty() ? Optional.empty() : Optional.of(body);
            }
        }
        return Optional.empty();
    }
}
