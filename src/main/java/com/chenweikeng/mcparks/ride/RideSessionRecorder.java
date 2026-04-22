package com.chenweikeng.mcparks.ride;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.ParkTracker;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Records comprehensive ride session data to a JSON file for later analysis.
 * Captures metadata, player path, every chat message with full style breakdown,
 * scoreboard state, and vehicle info.
 *
 * <p>Output: {@code logs/ride-sessions/<ride>-<timestamp>.json}
 *
 * <p>Designed so that Claude can later read and analyze the JSON to:
 * <ul>
 *   <li>Build new {@link RideExperience} classes from observed chat patterns</li>
 *   <li>Determine ride timing from path data</li>
 *   <li>Identify speaker prefixes and their color schemes</li>
 *   <li>Distinguish ride narration from player chat / system messages</li>
 * </ul>
 */
public final class RideSessionRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksSessionRecorder");
    private static final int POSITION_SAMPLE_TICKS = 40; // 2 seconds
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping().create();

    // Active session state
    private SessionData session;
    private long startTimeMs;
    private int tickCounter;
    private boolean recording;

    // Audio change tracking (detect start/stop events between snapshots)
    private Set<String> previousAudioTrackNames = new HashSet<>();

    // ---- Lifecycle ----

    /**
     * Called every client tick from the main mod class.
     * Handles starting/stopping recordings based on ride state.
     */
    public void tick(Minecraft client) {
        RideDetector detector = RideDetector.getInstance();
        if (detector == null) return;

        RideExperience exp = detector.getCurrentExperience();
        LocalPlayer player = client.player;

        // Start recording when a ride experience becomes active
        if (exp != null && !recording && player != null) {
            startSession(exp, detector, client);
        }

        // Stop recording when ride ends
        if (exp == null && recording) {
            stopSession();
            return;
        }

        // Sample position and audio
        if (recording && player != null) {
            tickCounter++;
            if (tickCounter >= POSITION_SAMPLE_TICKS) {
                tickCounter = 0;
                samplePosition(player);
                sampleAudio();
            }
        }
    }

    private void startSession(RideExperience exp, RideDetector detector, Minecraft client) {
        session = new SessionData();
        startTimeMs = System.currentTimeMillis();
        tickCounter = 0;
        recording = true;

        // Metadata
        session.rideName = exp.name();
        session.rideClass = exp.getClass().getSimpleName();
        ParkTracker tracker = ParkTracker.getInstance();
        session.parkCode = tracker.currentParkCode();
        session.parkName = tracker.currentPark();
        session.boardedAt = Instant.now().toString();
        session.playerName = client.getUser().getName();
        session.configuredRideTimeSec = exp.rideTimeSeconds();

        // Vehicle info
        LocalPlayer player = client.player;
        if (player != null && player.getVehicle() != null) {
            var vehicle = player.getVehicle();
            session.vehicleEntityId = vehicle.getId();
            session.vehicleType = vehicle.getType().toShortString();
        }

        // Nearby models from detector
        RideRegistry.Ride jsonRide = detector.getCurrentRide();
        if (jsonRide != null) {
            session.jsonRideName = jsonRide.name;
            session.jsonRideTimeSec = jsonRide.rideTimeSeconds;
        }

        // Scoreboard snapshot
        captureScoreboard(client);

        // First position sample
        if (player != null) {
            samplePosition(player);
        }

        // Initial audio snapshot
        previousAudioTrackNames.clear();
        sampleAudio();

        LOGGER.info("Started session recording for '{}'", exp.name());
    }

    private void stopSession() {
        if (session == null) return;

        long durationMs = System.currentTimeMillis() - startTimeMs;
        session.dismountedAt = Instant.now().toString();
        session.durationMs = durationMs;
        session.durationFormatted = formatDuration(durationMs);

        // Write async to avoid blocking the game thread
        SessionData data = session;
        recording = false;
        session = null;
        startTimeMs = 0;
        tickCounter = 0;

        Thread writer = new Thread(() -> writeSession(data), "ride-session-writer");
        writer.setDaemon(true);
        writer.start();
    }

    // ---- Chat message recording ----

    /**
     * Called from {@code ChatComponentMixin} for EVERY incoming chat message,
     * before any filtering or subtitle capture. Records the full message with
     * style breakdown and classification.
     *
     * @param message           the raw Component
     * @param capturedAsSubtitle whether this message was captured as a ride subtitle
     * @param cancelled          whether this message was cancelled (hidden from chat)
     */
    public void onChatMessage(Component message, boolean capturedAsSubtitle, boolean cancelled) {
        if (!recording || session == null) return;

        String text = message.getString();
        if (text.isEmpty()) return;

        ChatEntry entry = new ChatEntry();
        entry.elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
        entry.text = text;
        entry.classification = classifyMessage(text);
        entry.capturedAsSubtitle = capturedAsSubtitle;
        entry.cancelledFromChat = cancelled;

        // Root style
        entry.rootStyle = extractStyle(message.getStyle());

        // Siblings with full style info
        List<Component> siblings = message.getSiblings();
        if (!siblings.isEmpty()) {
            entry.siblings = new ArrayList<>(siblings.size());
            for (Component sib : siblings) {
                SiblingInfo si = new SiblingInfo();
                si.text = sib.getString();
                si.style = extractStyle(sib.getStyle());
                entry.siblings.add(si);
            }
        }

        // Detect speaker prefix pattern: [Name] body
        if (text.startsWith("[")) {
            int closeBracket = text.indexOf("] ");
            if (closeBracket > 0) {
                entry.detectedPrefix = text.substring(0, closeBracket + 2);
                entry.detectedBody = text.substring(closeBracket + 2);
            }
        }

        session.chatLog.add(entry);
    }

    /**
     * Returns true if currently recording a ride session.
     */
    public boolean isRecording() {
        return recording;
    }

    // ---- Position sampling ----

    private void samplePosition(LocalPlayer player) {
        if (session == null) return;

        // Use vehicle position for seat-independent path data;
        // fall back to player if no vehicle (shouldn't happen on a ride).
        net.minecraft.world.entity.Entity target = player.getVehicle();
        if (target == null) target = player;

        PositionSample sample = new PositionSample();
        sample.t = (System.currentTimeMillis() - startTimeMs) / 1000.0;
        sample.x = round3(target.getX());
        sample.y = round3(target.getY());
        sample.z = round3(target.getZ());
        sample.yaw = round1(player.getYRot());  // player look direction, not vehicle
        sample.pitch = round1(player.getXRot());
        session.path.add(sample);
    }

    // ---- Audio sampling ----

    /**
     * Snapshots all active audio tracks, records state changes (started/stopped),
     * and logs a periodic audio state entry alongside the position samples.
     */
    private void sampleAudio() {
        if (session == null) return;

        try {
            MCParksAudioService audioService = MCParksAudioService.getInstance();
            List<MCParksAudioService.ActiveTrack> tracks = audioService.snapshotActive();
            double elapsed = (System.currentTimeMillis() - startTimeMs) / 1000.0;

            // Build current track name set for change detection
            Set<String> currentNames = new HashSet<>();
            for (MCParksAudioService.ActiveTrack t : tracks) {
                currentNames.add(t.name());
            }

            // Detect newly started tracks
            for (MCParksAudioService.ActiveTrack t : tracks) {
                if (!previousAudioTrackNames.contains(t.name())) {
                    AudioEvent event = new AudioEvent();
                    event.elapsedSec = elapsed;
                    event.type = "started";
                    event.trackName = t.name();
                    event.url = t.url();
                    event.looping = t.looping();
                    event.serverVolume = t.serverVolume();
                    event.trackStartedAtMs = t.startedAtMs();
                    event.firstTriggerAtMs = t.firstTriggerAtMs();
                    session.audioEvents.add(event);
                }
            }

            // Detect stopped tracks
            for (String prev : previousAudioTrackNames) {
                if (!currentNames.contains(prev)) {
                    AudioEvent event = new AudioEvent();
                    event.elapsedSec = elapsed;
                    event.type = "stopped";
                    event.trackName = prev;
                    session.audioEvents.add(event);
                }
            }

            previousAudioTrackNames = currentNames;

            // Periodic snapshot of all active tracks
            if (!tracks.isEmpty()) {
                AudioSnapshot snapshot = new AudioSnapshot();
                snapshot.elapsedSec = elapsed;
                snapshot.tracks = new ArrayList<>(tracks.size());
                for (MCParksAudioService.ActiveTrack t : tracks) {
                    AudioTrackInfo info = new AudioTrackInfo();
                    info.name = t.name();
                    info.url = t.url();
                    info.looping = t.looping();
                    info.serverVolume = t.serverVolume();
                    info.startedAtMs = t.startedAtMs();
                    info.playingForSec = round1((System.currentTimeMillis() - t.startedAtMs()) / 1000.0);
                    info.firstTriggerAtMs = t.firstTriggerAtMs();
                    info.lastTriggerAtMs = t.lastTriggerAtMs();
                    info.triggerCount = t.triggerCount();
                    info.lastServerMessage = t.lastServerMessage();
                    info.active = t.active();
                    info.fadingOut = t.fadingOut();
                    snapshot.tracks.add(info);
                }
                session.audioLog.add(snapshot);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to sample audio state", e);
        }
    }

    // ---- Scoreboard capture ----

    private void captureScoreboard(Minecraft client) {
        if (client.level == null) return;
        try {
            Scoreboard sb = client.level.getScoreboard();
            Objective sidebar = sb.getDisplayObjective(1);
            if (sidebar == null) return;

            session.scoreboardTitle = sidebar.getDisplayName().getString();

            for (Score score : sb.getPlayerScores(sidebar)) {
                PlayerTeam team = sb.getPlayersTeam(score.getOwner());
                if (team == null) continue;
                String prefix = team.getPlayerPrefix().getString();
                String suffix = team.getPlayerSuffix().getString();

                ScoreboardEntry se = new ScoreboardEntry();
                se.score = score.getScore();
                se.display = (prefix + suffix).trim();
                se.prefix = prefix;
                se.suffix = suffix;
                session.scoreboard.add(se);
            }
            session.scoreboard.sort(Comparator.comparingInt((ScoreboardEntry e) -> e.score).reversed());
        } catch (Exception e) {
            LOGGER.debug("Failed to capture scoreboard", e);
        }
    }

    // ---- Classification ----

    private static String classifyMessage(String text) {
        // Player chat: "[Role] Name › message"
        if (text.contains(" \u203A ")) return "player_chat";

        // Join/leave system messages
        if (text.startsWith("[+] ") || text.startsWith("[-] ")) return "system_join_leave";

        // Reward/payout messages
        if (text.contains("\u20B1") // ₱
                || text.startsWith("You have received")
                || text.startsWith("You've ridden")) return "reward";

        // Speaker-prefixed ride dialogue: [Name] body
        if (text.startsWith("[") && text.contains("] ")) {
            int close = text.indexOf("] ");
            String inside = text.substring(1, close);
            // Heuristic: ride speakers have short names, no spaces-heavy content
            if (inside.length() < 30 && !inside.contains(" › ")) {
                return "speaker_prefixed";
            }
        }

        // Ride system messages
        if (text.startsWith("+ ") || text.startsWith("- ")) return "ride_system";

        // Everything else is a narration candidate
        return "narration_candidate";
    }

    // ---- Style extraction ----

    private static StyleInfo extractStyle(Style style) {
        if (style == null) return null;
        StyleInfo si = new StyleInfo();
        if (style.getColor() != null) {
            si.color = String.format("0x%06X", style.getColor().getValue());
            si.colorName = style.getColor().serialize();
        }
        si.italic = Boolean.TRUE.equals(style.isItalic()) ? true : null;
        si.bold = Boolean.TRUE.equals(style.isBold()) ? true : null;
        si.underlined = Boolean.TRUE.equals(style.isUnderlined()) ? true : null;
        si.strikethrough = Boolean.TRUE.equals(style.isStrikethrough()) ? true : null;
        si.obfuscated = Boolean.TRUE.equals(style.isObfuscated()) ? true : null;
        // Only include if there's something to report
        if (si.color == null && si.italic == null && si.bold == null
                && si.underlined == null && si.strikethrough == null && si.obfuscated == null) {
            return null;
        }
        return si;
    }

    // ---- File output ----

    private void writeSession(SessionData data) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Path logsDir = mc.gameDirectory.toPath().resolve("logs").resolve("ride-sessions");
            Files.createDirectories(logsDir);

            String safeName = data.rideName.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
            String timestamp = LocalDateTime.now().format(TS_FMT);
            Path outFile = logsDir.resolve(safeName + "-" + timestamp + ".json");

            try (BufferedWriter w = Files.newBufferedWriter(outFile)) {
                GSON.toJson(data, w);
            }

            LOGGER.info("Wrote ride session ({} chat, {} path, {} audio snapshots, {} audio events) -> {}",
                    data.chatLog.size(), data.path.size(),
                    data.audioLog.size(), data.audioEvents.size(), outFile);
        } catch (IOException e) {
            LOGGER.error("Failed to write ride session", e);
        }
    }

    // ---- Cleanup ----

    public void reset() {
        if (recording) {
            stopSession();
        }
    }

    // ---- Helpers ----

    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        return String.format("%dm %ds", s / 60, s % 60);
    }

    // ---- Data model (serialized to JSON) ----

    @SuppressWarnings("unused") // fields read by Gson
    private static class SessionData {
        String schemaVersion = "1";
        // Ride metadata
        String rideName;
        String rideClass;
        String parkCode;
        String parkName;
        String jsonRideName;
        int jsonRideTimeSec;
        int configuredRideTimeSec;
        String playerName;
        // Timing
        String boardedAt;
        String dismountedAt;
        long durationMs;
        String durationFormatted;
        // Vehicle
        int vehicleEntityId;
        String vehicleType;
        // Scoreboard at boarding
        String scoreboardTitle;
        List<ScoreboardEntry> scoreboard = new ArrayList<>();
        // Recorded data
        List<PositionSample> path = new ArrayList<>();
        List<ChatEntry> chatLog = new ArrayList<>();
        // Audio state over time
        List<AudioSnapshot> audioLog = new ArrayList<>();
        List<AudioEvent> audioEvents = new ArrayList<>();
    }

    @SuppressWarnings("unused")
    private static class PositionSample {
        double t;
        double x, y, z;
        double yaw, pitch;
    }

    @SuppressWarnings("unused")
    private static class ChatEntry {
        double elapsedSec;
        String text;
        String classification;
        boolean capturedAsSubtitle;
        boolean cancelledFromChat;
        StyleInfo rootStyle;
        List<SiblingInfo> siblings;
        String detectedPrefix;
        String detectedBody;
    }

    @SuppressWarnings("unused")
    private static class SiblingInfo {
        String text;
        StyleInfo style;
    }

    @SuppressWarnings("unused")
    private static class StyleInfo {
        String color;
        String colorName;
        Boolean italic;
        Boolean bold;
        Boolean underlined;
        Boolean strikethrough;
        Boolean obfuscated;
    }

    @SuppressWarnings("unused")
    private static class ScoreboardEntry {
        int score;
        String display;
        String prefix;
        String suffix;
    }

    /** Periodic snapshot of all active audio tracks at a moment in time. */
    @SuppressWarnings("unused")
    private static class AudioSnapshot {
        double elapsedSec;
        List<AudioTrackInfo> tracks;
    }

    /** Full state of a single audio track. */
    @SuppressWarnings("unused")
    private static class AudioTrackInfo {
        String name;
        String url;
        boolean looping;
        int serverVolume;
        long startedAtMs;
        double playingForSec;
        long firstTriggerAtMs;
        long lastTriggerAtMs;
        int triggerCount;
        String lastServerMessage;
        boolean active;
        boolean fadingOut;
    }

    /** Discrete audio event: a track starting or stopping. */
    @SuppressWarnings("unused")
    private static class AudioEvent {
        double elapsedSec;
        String type; // "started" or "stopped"
        String trackName;
        String url;             // only for "started"
        Boolean looping;        // only for "started"
        Integer serverVolume;   // only for "started"
        Long trackStartedAtMs;  // only for "started"
        Long firstTriggerAtMs;  // only for "started"
    }
}
