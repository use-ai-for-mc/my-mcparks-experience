package com.chenweikeng.mcparks.subtitle;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Plays pre-timed subtitles synchronized to audio tracks from
 * {@link MCParksAudioService}. Loaded from a JSON resource file that maps
 * audio track names to subtitle entries and ride-relative offsets.
 *
 * <p>Each tick, the player polls the audio service for active tracks,
 * checks which subtitle entry (if any) should be displayed based on
 * track playback elapsed time, and updates {@link SubtitleManager}.
 *
 * <p>Also estimates ride progress by computing:
 * {@code estimatedElapsed = trackRideOffset + trackPlaybackElapsed},
 * which self-corrects every time the server triggers a new audio track.
 *
 * <p>This replaces chat-based subtitle capture for rides that have
 * complete, pre-authored subtitle tracks (e.g. Living with the Land).
 */
public final class TimedSubtitlePlayer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksTimedSubs");

    /** A single timed subtitle entry within an audio track. */
    private record SubEntry(long startMs, long endMs, String text) {}

    /**
     * Subtitle entries plus ride offset for one audio track.
     *
     * <p>{@code loopDurationMs} is only meaningful for looping tracks (e.g.
     * boarding-loop audio). When {@code > 0}, the player wraps the track's
     * playback position by this value before matching entries, so the
     * subtitles replay on each loop. 0 means "do not wrap" (fallback
     * behaviour; entries stop matching after their last {@code endMs}).
     */
    private record TrackData(List<SubEntry> entries, long rideOffsetMs, long loopDurationMs) {}

    /** Audio track name -> subtitle + offset data. */
    private Map<String, TrackData> trackData = Collections.emptyMap();

    /** Total ride duration in seconds (from JSON). */
    private int totalRideTimeSec;

    /** Whether we are currently driving the subtitle display. */
    private boolean active;

    /** The text we last pushed to SubtitleManager (avoid redundant calls). */
    private String lastSetText;

    /** System time at which to clear the subtitle after a gap. 0 = no pending clear. */
    private long clearAtMs;

    /** How long to keep the last subtitle visible after its entry ends (ms). */
    private static final long LINGER_MS = 5000;

    // ---- Progress estimation ----

    /**
     * Estimated ride elapsed time in milliseconds, computed from the most
     * recently started known audio track. Updated every tick. -1 if unknown.
     */
    private long estimatedElapsedMs = -1;

    /**
     * Fallback: system time when the player was loaded (ride start).
     * Used if no audio tracks have been seen yet.
     */
    private long loadedAtMs;

    // ---- Loading ----

    /**
     * Loads subtitle data from a JSON resource on the classpath.
     *
     * @param resourcePath e.g. {@code "/assets/my-mcparks-experience/subtitles/lwtl.json"}
     * @return true if loaded successfully
     */
    public boolean load(String resourcePath) {
        try (InputStream in = TimedSubtitlePlayer.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.error("Subtitle resource not found: {}", resourcePath);
                return false;
            }

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);

            // Total ride time
            if (root.has("totalRideTimeSec")) {
                totalRideTimeSec = root.get("totalRideTimeSec").getAsInt();
            }

            JsonObject tracks = root.getAsJsonObject("tracks");
            if (tracks == null) {
                LOGGER.error("No 'tracks' object in subtitle resource");
                return false;
            }

            Map<String, TrackData> parsed = new HashMap<>();
            int totalEntries = 0;

            for (Map.Entry<String, JsonElement> entry : tracks.entrySet()) {
                String trackName = entry.getKey();
                JsonObject trackObj = entry.getValue().getAsJsonObject();

                // Parse ride offset
                long rideOffsetMs = 0;
                if (trackObj.has("rideOffsetMs")) {
                    rideOffsetMs = trackObj.get("rideOffsetMs").getAsLong();
                }

                // Parse optional loop duration (for looping tracks)
                long loopDurationMs = 0;
                if (trackObj.has("loopDurationMs")) {
                    loopDurationMs = trackObj.get("loopDurationMs").getAsLong();
                }

                // Parse subtitle entries
                JsonArray arr = trackObj.getAsJsonArray("entries");
                List<SubEntry> entries = new ArrayList<>(arr.size());
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    long startMs = obj.get("startMs").getAsLong();
                    long endMs = obj.get("endMs").getAsLong();
                    String text = obj.get("text").getAsString();
                    entries.add(new SubEntry(startMs, endMs, text));
                }

                // Sort by start time (should already be sorted, but ensure)
                entries.sort(Comparator.comparingLong(SubEntry::startMs));
                parsed.put(trackName, new TrackData(entries, rideOffsetMs, loopDurationMs));
                totalEntries += entries.size();
            }

            trackData = parsed;
            active = false;
            lastSetText = null;
            clearAtMs = 0;
            estimatedElapsedMs = -1;
            loadedAtMs = System.currentTimeMillis();

            LOGGER.info("Loaded {} subtitle tracks ({} entries, totalTime={}s) from {}",
                    parsed.size(), totalEntries, totalRideTimeSec, resourcePath);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load subtitle resource: {}", resourcePath, e);
            return false;
        }
    }

    // ---- Tick ----

    /**
     * Called every client tick while the ride is active.
     * Polls audio state and updates subtitle display + progress estimation.
     */
    public void tick() {
        if (trackData.isEmpty()) return;

        active = true;

        MCParksAudioService audio = MCParksAudioService.getInstance();
        List<MCParksAudioService.ActiveTrack> activeTracks = audio.snapshotActive();

        long now = System.currentTimeMillis();
        String bestText = null;
        long bestStartTime = 0; // prefer the most recently started track

        // Also track the best progress estimate from the most recently started known track
        long bestProgressTrackStart = 0;
        long bestRideElapsedMs = -1;

        for (MCParksAudioService.ActiveTrack track : activeTracks) {
            if (!track.active()) continue;

            TrackData data = trackData.get(track.name());
            if (data == null) continue;

            // Prefer the audio mixer's actual playback position; fall back to
            // wall clock before the line opens (positionMs == 0 at startup).
            long elapsedMs = track.positionMs() > 0
                    ? track.positionMs()
                    : now - track.startedAtMs();

            // Looping tracks (e.g. boarding loop) need their position wrapped
            // so subtitles replay on each iteration.
            if (track.looping() && data.loopDurationMs > 0) {
                elapsedMs = elapsedMs % data.loopDurationMs;
            }

            // -- Progress estimation --
            // Use the most recently started track for the best estimate
            if (track.startedAtMs() > bestProgressTrackStart) {
                bestProgressTrackStart = track.startedAtMs();
                bestRideElapsedMs = data.rideOffsetMs + elapsedMs;
            }

            // -- Subtitle matching --
            for (SubEntry entry : data.entries) {
                if (elapsedMs >= entry.startMs && elapsedMs < entry.endMs) {
                    if (bestText == null || track.startedAtMs() > bestStartTime) {
                        bestText = entry.text;
                        bestStartTime = track.startedAtMs();
                    }
                    break;
                }
                if (elapsedMs >= entry.endMs) continue;
                if (elapsedMs < entry.startMs) break;
            }
        }

        // Update progress estimate
        if (bestRideElapsedMs >= 0) {
            estimatedElapsedMs = bestRideElapsedMs;
        } else if (estimatedElapsedMs >= 0) {
            // No known track is currently active, but we had a previous estimate.
            // Increment by tick time (~50ms) to keep the countdown smooth.
            // This avoids the display freezing between audio tracks.
            estimatedElapsedMs += 50; // approximately one tick
        }
        // else: no estimate yet; getEstimatedElapsedSeconds() falls back to wall clock

        // Update subtitle display
        if (bestText != null) {
            if (!bestText.equals(lastSetText)) {
                SubtitleManager.setTimedCaption(bestText);
                lastSetText = bestText;
            }
            clearAtMs = 0;
        } else if (lastSetText != null) {
            if (clearAtMs == 0) {
                clearAtMs = now + LINGER_MS;
            } else if (now >= clearAtMs) {
                SubtitleManager.clearTimedCaption();
                lastSetText = null;
                clearAtMs = 0;
            }
        }
    }

    // ---- Progress queries (for RideHudRenderer) ----

    /**
     * Estimated elapsed ride time in seconds, based on audio track offsets.
     * Returns -1 if no estimate is available.
     */
    public int getEstimatedElapsedSeconds() {
        if (estimatedElapsedMs >= 0) {
            return (int) (estimatedElapsedMs / 1000);
        }
        // Fallback: wall clock since load
        if (loadedAtMs > 0) {
            return (int) ((System.currentTimeMillis() - loadedAtMs) / 1000);
        }
        return -1;
    }

    /**
     * Estimated remaining ride time in seconds.
     * Returns -1 if no estimate is available.
     */
    public int getEstimatedRemainingSeconds() {
        if (totalRideTimeSec <= 0) return -1;
        int elapsed = getEstimatedElapsedSeconds();
        if (elapsed < 0) return -1;
        return Math.max(0, totalRideTimeSec - elapsed);
    }

    /**
     * Total ride duration in seconds from the subtitle data.
     */
    public int getTotalRideTimeSec() {
        return totalRideTimeSec;
    }

    /**
     * Whether the player has an audio-based progress estimate
     * (as opposed to just wall-clock fallback).
     */
    public boolean hasAudioBasedEstimate() {
        return estimatedElapsedMs >= 0;
    }

    // ---- Lifecycle ----

    /**
     * Returns true if this player has subtitle data loaded.
     */
    public boolean isLoaded() {
        return !trackData.isEmpty();
    }

    /**
     * Reset state when the ride ends.
     */
    public void reset() {
        if (active && lastSetText != null) {
            SubtitleManager.clearTimedCaption();
        }
        active = false;
        lastSetText = null;
        clearAtMs = 0;
        estimatedElapsedMs = -1;
        loadedAtMs = 0;
        totalRideTimeSec = 0;
        trackData = Collections.emptyMap();
    }
}
