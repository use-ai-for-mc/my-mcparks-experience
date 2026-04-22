package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;

/**
 * The Twilight Zone Tower of Terror &mdash; the drop-tower dark ride at
 * Disney's Hollywood Studios (WDW). Covers boarding through exit; the
 * library pre-show is a separate {@link TowerOfTerrorPreshow} sibling
 * class so that the player-controlled walk from the library to the
 * loading platform doesn't get baked into this ride's fixed timeline.
 *
 * <p>Vehicle: invisible armor stand with {@code iron_axe:227}
 * ({@code vehicles/tot_wdw} in the resource pack).
 *
 * <p>Detection has two paths, both WDW-park-gated:
 * <ol>
 *   <li><b>Loading platform:</b> the {@code TowerOfTerror/BoilerRoomLoadWarning}
 *       safety-spiel audio is playing. This fires before the guest has
 *       taken a seat, so the bilingual safety subtitles display as
 *       soon as the audio starts &mdash; not 20+ seconds later when
 *       the guest finally buckles in.</li>
 *   <li><b>Boarded:</b> passenger of the service elevator
 *       ({@code iron_axe:227}). Covers the rest of the ride through
 *       dismount.</li>
 * </ol>
 *
 * <p>The MCParks WDW scoreboard does <em>not</em> publish a "Current
 * Ride" entry for ToT (verified across multiple sessions), so we
 * cannot match on the sidebar &mdash; same situation
 * {@link SpaceshipEarth} documents.
 *
 * <p>Subtitles are driven by
 * {@code assets/my-mcparks-experience/subtitles/tot.json} (timed,
 * audio-synced). The {@code [Rod Serling]} chat messages MCParks emits
 * on-ride duplicate the voiced narration, so {@link #captureSubtitle}
 * matches them and the chat mixin silently cancels them from the chat
 * pane; display is entirely timed-driven.
 *
 * <p>Audio tracks covered by the JSON, in order of play:
 * <ul>
 *   <li>{@code TowerOfTerror/BoilerRoomLoadWarning} (47 s, bilingual
 *       safety spiel).</li>
 *   <li>{@code TowerOfTerror/corridor} (73 s) &mdash; ascent narration.</li>
 *   <li>{@code TowerOfTerror/fifth} (60 s) &mdash; fifth-dimension /
 *       pre-drop.</li>
 *   <li>{@code TowerOfTerror/drop1a} + {@code drop1b} (~52 s, in parallel)
 *       &mdash; drop sequence, music/SFX only, no subtitles authored.</li>
 *   <li>{@code TowerOfTerror/post} (46 s) &mdash; post-drop farewell.</li>
 * </ul>
 *
 * <p>The broken {@code TowerOfTerror/BoilerRoomLoop} ambient track
 * (server announces it but the mp3 URL 404s, hence cache-validation
 * spam in the game log) is intentionally ignored &mdash; it carries
 * no dialogue.
 */
public class TowerOfTerror implements RideExperience {

    private static final String NAME = "The Twilight Zone Tower of Terror";
    private static final String PARK = "Walt Disney World Resort";

    /** Service elevator: iron_axe with damage 227. */
    private static final String VEHICLE_ITEM = "iron_axe";
    private static final int VEHICLE_DAMAGE = 227;

    /** Named chat speaker prefixes (cancelled from chat; display is timed-driven). */
    private static final String[] SPEAKER_PREFIXES = {
        "[Rod Serling] "
    };

    /**
     * Audio tracks that activate the experience pre-boarding. The safety
     * spiel starts playing while the guest is still walking onto the
     * loading platform, so gating subtitles on {@code isPassenger} alone
     * would delay the bilingual safety subtitles by 10-20+ seconds.
     * {@link TowerOfTerrorPreshow} owns the library {@code TOTPreshow}
     * track separately and is not included here.
     */
    private static final Set<String> LOADING_TRACKS = Set.of(
            "TowerOfTerror/BoilerRoomLoadWarning"
    );

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/tot.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }

    /** Boarding → dismount: 4 min 7 sec (247 s) observed. JSON's {@code totalRideTimeSec} takes precedence for HUD. */
    @Override public int rideTimeSeconds() { return 247; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        // Boarded: passenger of the service elevator.
        if (ctx.isPassenger) {
            for (ExperienceContext.NearbyModel m : ctx.nearbyModels) {
                if (m.matches(VEHICLE_ITEM, VEHICLE_DAMAGE)) return true;
            }
        }
        // Loading platform: safety spiel audio is playing, guest hasn't
        // taken a seat yet. Activate so the bilingual safety subtitles
        // display in sync with the audio rather than starting mid-spiel
        // once the player finally buckles in.
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && LOADING_TRACKS.contains(t.name())) return true;
        }
        return false;
    }

    /**
     * MCParks emits {@code [Rod Serling]} chat lines that duplicate the
     * voiced narration on the audio bed. Returning a present Optional
     * here suppresses them from the chat pane; the
     * {@link com.chenweikeng.mcparks.subtitle.TimedSubtitlePlayer} drives
     * the actual subtitle display (because {@link #subtitleResource()} is
     * non-null).
     */
    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();
        for (String prefix : SPEAKER_PREFIXES) {
            if (text.startsWith(prefix)) {
                String body = text.substring(prefix.length()).trim();
                return body.isEmpty() ? Optional.empty() : Optional.of(body);
            }
        }
        return Optional.empty();
    }
}
