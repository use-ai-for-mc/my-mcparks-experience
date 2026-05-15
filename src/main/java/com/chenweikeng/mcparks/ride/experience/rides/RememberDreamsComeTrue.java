package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.firework.FireworkLocations;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;

/**
 * Remember&hellip; Dreams Come True &mdash; the Disneyland (California)
 * 50th-anniversary fireworks show in front of Sleeping Beauty Castle, narrated
 * by Julie Andrews with cameos from Cinderella, Snow White, Ariel, Peter Pan,
 * Pinocchio, Aladdin, Walt Disney, and a parade of attraction-specific voices
 * (Conductor, Tiki Room trio, Ghost Host, Madame Leota, Pirate Captain,
 * Mad Hatter / March Hare, Tigger, Jack Wagner, Submarine crew). Theater-style
 * &mdash; guests stand at the hub viewing spot, no boarding event.
 *
 * <p><b>Detection.</b> Activates while any of the seven main show segments
 * (RDCT/1&hellip;RDCT/7) or the post-show music tail (RDCT/Post) is playing
 * AND the player is at the Disneyland castle viewing spot (Walt-statue
 * check). The seven segments play strictly sequentially with sub-second
 * server-side handoffs; RDCT/Post follows immediately after RDCT/7 stops as
 * the closing music bed and is included in the gate so the experience
 * extends through the music tail.
 *
 * <p>Excluded from the gate:
 * <ul>
 *   <li>RDCT preshow tracks &mdash; covered by the sibling
 *       {@link RememberDreamsComeTruePreshow} class.</li>
 *   <li>{@code RDCT/Pre} &mdash; a connection-handshake stinger fired at
 *       every WebSocket connect; not show content.</li>
 * </ul>
 *
 * <p>Subtitle data is in
 * {@code assets/my-mcparks-experience/subtitles/rdct.json}, anchored to
 * {@code RDCT/1} = T+0 with totalRideTimeSec=1123 (including the observed
 * RDCT/Post tail).
 */
public class RememberDreamsComeTrue implements RideExperience {

    private static final String NAME = "Remember… Dreams Come True";
    private static final String PARK = "Disneyland Resort";

    /**
     * Seven main-show segments plus the post-show music tail. Segments 1&ndash;7
     * are one-shot show frames (server-stopped or natural-finish handoffs);
     * RDCT/Post is the closing music bed that begins one second after RDCT/7
     * stops and is RDCT-namespaced so it doesn't false-positive other shows.
     */
    private static final Set<String> SHOW_TRACKS = Set.of(
            "RDCT/1",
            "RDCT/2",
            "RDCT/3",
            "RDCT/4",
            "RDCT/5",
            "RDCT/6",
            "RDCT/7",
            "RDCT/Post"
    );

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/rdct.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }
    @Override public boolean isTheaterMode() { return true; }

    /**
     * RDCT/1 start to RDCT/Post last observed sample = 1123&nbsp;s. RDCT/Post
     * was still playing when the 2026-04-26 capture ended, so the true total
     * may be slightly longer; HUD will use the JSON value once subtitles load.
     */
    @Override public int rideTimeSeconds() { return 1123; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        if (!hasShowAudio()) return false;
        return FireworkLocations.DISNEYLAND_CALIFORNIA.isHereNow(ctx.mc);
    }

    private static boolean hasShowAudio() {
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && SHOW_TRACKS.contains(t.name())) return true;
        }
        return false;
    }

    /**
     * MCParks publishes every voiced line as a {@code [Speaker] body} chat
     * message; the chat mixin suppresses them so the timed subtitle player
     * can drive the on-screen display from {@link #subtitleResource()}. Any
     * line that opens with a bracketed speaker tag is treated as voiced
     * dialogue; the dozens of distinct character speakers in this show make
     * a generic prefix match cleaner than enumerating each one.
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
