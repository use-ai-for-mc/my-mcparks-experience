package com.chenweikeng.mcparks.ride.experience;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * A programmable per-ride filter: detection + chat/subtitle routing + lifecycle
 * hooks. Each concrete ride (e.g. {@code DisneylandRailroad}) implements this
 * interface to declare "am I active right now?" and "what should this chat
 * message become for me?".
 *
 * <p>Registered in {@link RideExperienceRegistry}. Consulted every tick by
 * {@code RideDetector} (for ride-time HUD / onBoard / onDismount) and on every
 * incoming chat message by {@code ChatComponentMixin} (for subtitle capture).
 *
 * <p>Keep implementations cheap &mdash; {@link #isActive} is called every tick
 * and every chat message.
 */
public interface RideExperience {

    /** Human-readable ride name shown in HUD. */
    String name();

    /** Park name &mdash; must match the value from {@link ParkTracker#currentPark()}. */
    String park();

    /** Expected duration in seconds; {@code -1} if unknown (no HUD timer). */
    default int rideTimeSeconds() {
        return -1;
    }

    /**
     * Is the player currently on this ride? Called every tick and on every
     * chat message. Return {@code false} unless every precondition is met
     * (right park, right ride id, player is passenger, etc.) &mdash; the
     * registry iterates in registration order and picks the first match.
     */
    boolean isActive(ExperienceContext ctx);

    /**
     * Inspect an incoming chat message and, if this ride wants to turn it into
     * a subtitle, return the text to display. Only consulted while
     * {@link #isActive} is {@code true}. Return {@code Optional.empty()} to let
     * the message pass through to normal chat unchanged.
     *
     * <p>When {@link #subtitleResource()} is non-null (timed subtitle mode),
     * this method is still called but the returned text is <b>not</b> displayed
     * as a subtitle. Returning a value still causes the chat message to be
     * cancelled (hidden from the chat pane). This lets the experience suppress
     * narration messages from chat while audio-synced subtitles handle display.
     */
    default Optional<String> captureSubtitle(Component message) {
        return Optional.empty();
    }

    /**
     * Classpath resource path to a JSON file containing timed subtitle data
     * keyed by audio track name. When non-null, the mod uses audio-triggered
     * subtitles instead of chat-based capture for this ride.
     *
     * <p>Format: {@code {"tracks": {"trackName": [{"startMs":0,"endMs":5000,"text":"..."},...]}}}
     *
     * @return resource path (e.g. {@code "/assets/my-mcparks-experience/subtitles/lwtl.json"}),
     *         or {@code null} to use chat-based subtitles
     */
    default String subtitleResource() {
        return null;
    }

    /** Fired once when the player transitions onto this ride. */
    default void onBoard(ExperienceContext ctx) {}

    /** Fired once when the player dismounts. {@code durationMs} is how long they were on. */
    default void onDismount(ExperienceContext ctx, long durationMs) {}
}
