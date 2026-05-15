package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;

/**
 * Walt Disney's Enchanted Tiki Room &mdash; the WDW Adventureland
 * Audio-Animatronic show, covered as a single continuous experience from
 * the outdoor pre-show queue (Claude / Clyde / Pele incantation) through
 * the theater greeting and all five recorded show segments
 * (WDWS1&hellip;WDWS5) to the &ldquo;Heigh-Ho&rdquo; exit march.
 *
 * <p>The Tiki Room is a theater show, not a vehicle ride: guests stand in
 * the round room with no boarding event, so {@code isPassenger} stays
 * {@code false} throughout. Detection therefore relies entirely on the
 * audio-track allowlist; any of the nine tracks listed in
 * {@link #SHOW_TRACKS} keeps the experience active.
 *
 * <p><b>Audio sequence as the &ldquo;still on ride&rdquo; check.</b> The
 * timer starts when {@code TikiRoom/WDWPreshow} fires and continues
 * across the rest of the sequence (intro &rarr; CM/King &rarr; WDWS1-S5).
 * If the player walks away mid-pre-show, the audio stops and the next
 * track in the sequence never fires &mdash; the experience deactivates and
 * the ride ends &ldquo;unsuccessfully&rdquo; with a partial elapsed time.
 * Players who follow the audio through to WDWS5&rsquo;s last
 * &ldquo;Heigh-Ho&rdquo; refrain see the timer count up to ~17:22.
 *
 * <p>TODO: tighten detection by adding a location signal (a marker armor
 * stand inside the show building) once we identify one. The audio gate
 * works but is loose &mdash; another player&rsquo;s Tiki audio in the same
 * park instance would currently activate the experience for the local
 * player.
 *
 * <p>Subtitle and ride-time data is in
 * {@code assets/my-mcparks-experience/subtitles/tikiroom.json}. MCParks
 * already prints every voiced line as a {@code [Speaker] } chat message,
 * so {@link #captureSubtitle} matches those and the chat mixin silently
 * cancels them; the {@link com.chenweikeng.mcparks.subtitle.TimedSubtitlePlayer}
 * drives the actual on-screen display synced to audio playback.
 */
public class TikiRoom implements RideExperience {

    private static final String NAME = "Walt Disney's Enchanted Tiki Room";
    private static final String PARK = "Walt Disney World Resort";

    /**
     * Audio tracks that put the player on the Tiki Room experience &mdash;
     * pre-show queue, theater greeting, the five show segments, and the
     * fade-out variant of the intro.
     */
    private static final Set<String> SHOW_TRACKS = Set.of(
            "TikiRoom/WDWPreshow",
            "TikiRoom/WDWIntro",
            "TikiRoom/WDWIntroFade",
            "TikiRoom/CM/King",
            "TikiRoom/TikiRoom_WDWS1",
            "TikiRoom/TikiRoom_WDWS2",
            "TikiRoom/TikiRoom_WDWS3",
            "TikiRoom/TikiRoom_WDWS4",
            "TikiRoom/TikiRoom_WDWS5"
    );

    /**
     * Speaker prefixes voiced across the experience. Pre-show speakers
     * (Announcer / Claude / Clyde) and theater speakers
     * (Cast Member / Jos&eacute; / Michael / Pierre / Fritz) are listed
     * together so the chat mixin can suppress every duplicated chat line
     * regardless of which phase the player is in.
     */
    private static final String[] SPEAKER_PREFIXES = {
        "[Announcer] ",
        "[Cast Member] ",
        "[Claude] ",
        "[Clyde] ",
        "[José] ",
        "[Michael] ",
        "[Pierre] ",
        "[Fritz] "
    };

    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/tikiroom.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }

    /**
     * Pre-show + theater intro + CM/King + WDWS1..WDWS5: 1042 s (~17:22),
     * derived from observed cache-MISS timestamps. The JSON's
     * {@code totalRideTimeSec} is the source of truth for HUD progress;
     * this value is a fallback.
     */
    @Override public int rideTimeSeconds() { return 1042; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        for (MCParksAudioService.ActiveTrack t : MCParksAudioService.getInstance().snapshotActive()) {
            if (t.active() && SHOW_TRACKS.contains(t.name())) return true;
        }
        return false;
    }

    /**
     * Suppresses chat duplicates of voiced lines (named speaker prefixes
     * plus song lyrics that arrive prefixed with {@code ♫}). The returned
     * string is unused &mdash; display is timed-driven via
     * {@link #subtitleResource()} &mdash; we just need a non-empty Optional
     * so the chat mixin cancels the message.
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
        if (text.startsWith("♫")) {
            return Optional.of(text);
        }
        return Optional.empty();
    }
}
