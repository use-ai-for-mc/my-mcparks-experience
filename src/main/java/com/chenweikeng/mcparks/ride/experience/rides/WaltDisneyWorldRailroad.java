package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;

/**
 * Walt Disney World Railroad &mdash; the grand-circle steam-train tour around
 * the Magic Kingdom, calling at Main Street, Frontierland, and Fantasyland
 * (Storybook Circus). Four historic engines (Walter E. Disney, Lilly Belle,
 * Roy O. Disney, Roger E. Broggie), two tender colours (red/green), and
 * four passenger-car colours (red/green/blue/yellow) all belong to this ride.
 *
 * <p><b>Detection.</b> Passenger of any car in the {@code vehicles/wdw/railroad/*}
 * family &mdash; ten {@code diamond_axe} damage values, see
 * {@link #VEHICLE_DAMAGES}. A scoreboard ride-name match is also attempted as
 * a belt-and-braces fallback.
 *
 * <p><b>Subtitles &mdash; hybrid mode.</b>
 * All trip narration is voiced in audio tracks named
 * {@code MK/RR/{MainStreet,Frontierland,Fantasyland}/1-5} (13 total) plus
 * generic depot spiels at each station ({@code .../Depot/NowBoarding} and
 * {@code .../Depot/LastCall}). The chat log mirrors every voiced line, so
 * display is driven by {@code assets/my-mcparks-experience/subtitles/wdw_railroad.json}
 * (timed, audio-synced via {@link com.chenweikeng.mcparks.subtitle.TimedSubtitlePlayer})
 * and {@link #captureSubtitle} suppresses the duplicate chat text from the pane.
 *
 * <p><b>Depot-audio aliasing.</b> MCParks' server emits three distinct track
 * names for station spiels (one per station), but the mp3 payload is byte-
 * identical at all three. The JSON therefore lists the same {@code entries}
 * under all three keys so subtitles fire correctly regardless of which depot
 * track name the server uses at a given station.
 *
 * <p><b>Show-scene voices.</b> Frontierland/4 contains a Peter Pan + Wendy
 * Darling vignette, and Fantasyland/2 has a Tomorrowland {@code [Computer]}
 * interjection that briefly speaks over the Conductor. Their chat prefixes
 * are suppressed here and the corresponding audio-track entries carry the
 * voice-attribution text (e.g. {@code "Peter Pan: There it is!"}).
 *
 * <p><b>PA station spiels.</b> Depot announcements arrive as unprefixed plain
 * text, indistinguishable by style from system chat. We suppress them by
 * matching the small, distinctive phrase set in {@link #PA_PREFIXES}.
 */
public class WaltDisneyWorldRailroad implements RideExperience {

    private static final String NAME = "Walt Disney World Railroad";
    private static final String PARK = "Walt Disney World Resort";

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/wdw_railroad.json";

    /** All WDW Railroad vehicles are {@code diamond_axe} armor-stand items. */
    private static final String VEHICLE_ITEM = "diamond_axe";

    /**
     * Damage values for the ten WDW Railroad models defined in the resource
     * pack at {@code vehicles/wdw/railroad/*} &mdash; four engines, two
     * tenders, four passenger cars. The conductor seat itself is a bare
     * {@code ArmorStand} marker with no equipment or tags, so it can't be
     * matched directly; including the engine/tender damages here is what
     * makes conductor-seat detection work via the 5-block scan radius in
     * {@link com.chenweikeng.mcparks.ride.RideDetector}.
     */
    private static final int[] VEHICLE_DAMAGES = {
            201, // mk_passenger_red   (confirmed 2026-04-21, HEAD)
            202, // wed_mkengine       (Walter E. Disney,    confirmed 2026-04-21, OFFHAND)
            203, // mktender_red       (confirmed 2026-04-21, OFFHAND)
            207, // lb_mkengine        (Lilly Belle,         confirmed 2026-04-21, OFFHAND)
            208, // mktender_green     (confirmed 2026-04-21, OFFHAND)
            209, // reb_mkengine       (Roy O. Disney,       confirmed 2026-04-21)
            210, // rod_mkengine       (Roger E. Broggie,    confirmed 2026-04-21)
            211, // mk_passenger_green (confirmed 2026-04-21, HEAD)
            212, // mk_passenger_blue  (confirmed 2026-04-21)
            213  // mk_passenger_yellow(confirmed 2026-04-21)
    };

    /**
     * Named chat speakers &mdash; all four voice lines already live in the
     * timed-subtitle JSON, so chat is cancelled and the JSON drives display.
     */
    private static final String[] SPEAKER_PREFIXES = {
            "[Conductor] ",
            "[Peter Pan] ",
            "[Wendy Darling] ",
            "[Computer] "
    };

    /**
     * Depot-spiel audio tracks &mdash; the station PA announcements that
     * play while a train is loading or about to depart. These act as a
     * preshow: the {@link #isActive} gate drops the {@code isPassenger}
     * requirement while any of these is playing, so guests waiting on the
     * platform get their subtitles even before they board. Once the guest
     * actually boards, the passenger/vehicle path keeps the experience
     * active for the ride proper.
     *
     * <p>All six track names are enumerated so depot audio at any of the
     * three stations (Main Street, Frontierland, Fantasyland) triggers
     * preshow activation.
     */
    private static final Set<String> DEPOT_TRACKS = Set.of(
            "MK/RR/MainStreet/Depot/NowBoarding",
            "MK/RR/MainStreet/Depot/LastCall",
            "MK/RR/Frontierland/Depot/NowBoarding",
            "MK/RR/Frontierland/Depot/LastCall",
            "MK/RR/Fantasyland/Depot/NowBoarding",
            "MK/RR/Fantasyland/Depot/LastCall"
    );

    /**
     * Unprefixed depot-PA lines the server emits as plain chat. These mirror
     * the {@code .../Depot/NowBoarding} and {@code .../Depot/LastCall}
     * audio tracks, so we suppress them from the chat pane; the JSON drives
     * the timed display. Matched via {@code startsWith} so minor punctuation
     * variants are covered. All strings are distinctive enough not to collide
     * with Conductor narration or system messages.
     */
    private static final String[] PA_PREFIXES = {
            "Your Attention Please",
            "Your attention please",
            "The Walt Disney World Railroad",
            "Now boarding for a scenic trip",
            "Last call for the Walt Disney World Railroad",
            "Now departing for a grand circle tour",
            "Last Call.",
            "All aboard",
            "All aboooo",
            "Board!"
    };

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }

    /** Boarding &rarr; dismount: 16 min 42 sec (1002 s), observed. */
    @Override public int rideTimeSeconds() { return 1002; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;

        // Preshow: a depot NowBoarding / LastCall spiel is playing. The
        // client only receives these audio commands when it's physically
        // near a station, so we can treat "track is active" as a reliable
        // proxy for "player is at a depot right now". Activate regardless
        // of passenger state so guests waiting on the platform get
        // subtitles for the PA announcement.
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && DEPOT_TRACKS.contains(t.name())) return true;
        }

        // Main ride: passenger of any WDW Railroad vehicle.
        if (!ctx.isPassenger) return false;

        for (ExperienceContext.NearbyModel m : ctx.nearbyModels) {
            for (int d : VEHICLE_DAMAGES) {
                if (m.matches(VEHICLE_ITEM, d)) return true;
            }
        }

        return false;
    }

    /**
     * Suppresses every chat line the on-ride audio has already voiced &mdash;
     * Conductor narration, the Peter&nbsp;Pan / Wendy / Computer show-scene
     * vignettes, and the unprefixed depot PA spiels. Returning a present
     * {@link Optional} cancels the message from the chat pane; because
     * {@link #subtitleResource()} is non-null, the returned string is ignored
     * and the {@link com.chenweikeng.mcparks.subtitle.TimedSubtitlePlayer}
     * drives the actual caption.
     */
    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();

        // 1. Named speaker prefixes (Conductor, Peter Pan, Wendy Darling, Computer)
        for (String prefix : SPEAKER_PREFIXES) {
            if (text.startsWith(prefix)) {
                String body = text.substring(prefix.length()).trim();
                return body.isEmpty() ? Optional.empty() : Optional.of(body);
            }
        }

        // 2. Unprefixed depot-PA lines (plain text, no style)
        //    Guard against player chat first — MCParks player lines contain
        //    " \u203A " (‹ Name › message), which PA lines never do.
        if (!text.contains(" \u203A ")) {
            for (String pa : PA_PREFIXES) {
                if (text.startsWith(pa)) {
                    return Optional.of(text);
                }
            }
        }

        return Optional.empty();
    }
}
