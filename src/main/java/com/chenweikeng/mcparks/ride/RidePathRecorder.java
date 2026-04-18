package com.chenweikeng.mcparks.ride;

import com.chenweikeng.mcparks.ride.experience.RideExperience;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Records player coordinates at regular intervals while on a ride,
 * writing a CSV log file for later analysis. Used to build
 * position-based progress tracking for rides with variable pacing.
 *
 * <p>Output: {@code logs/ride-paths/<ride>-<timestamp>.csv} with columns
 * {@code elapsed_sec, x, y, z, yaw, pitch}.
 *
 * <p>Sampling rate: every {@link #SAMPLE_INTERVAL_TICKS} client ticks
 * (default 40 = 2 seconds at 20 TPS).
 */
public final class RidePathRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksPathRecorder");
    private static final int SAMPLE_INTERVAL_TICKS = 40; // 2 seconds
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private BufferedWriter writer;
    private String activeRideName;
    private long startTimeMs;
    private int tickCounter;

    /**
     * Called every client tick. Starts/stops recording based on the
     * current ride experience from {@link RideDetector}.
     */
    public void tick(Minecraft client) {
        RideDetector detector = RideDetector.getInstance();
        if (detector == null) return;

        RideExperience exp = detector.getCurrentExperience();
        LocalPlayer player = client.player;

        // Start recording when a ride becomes active
        if (exp != null && writer == null && player != null) {
            startRecording(exp, client);
        }

        // Stop recording when ride ends
        if (exp == null && writer != null) {
            stopRecording();
            return;
        }

        // Sample coordinates
        if (writer != null && player != null) {
            tickCounter++;
            if (tickCounter >= SAMPLE_INTERVAL_TICKS) {
                tickCounter = 0;
                writeSample(player);
            }
        }
    }

    private void startRecording(RideExperience exp, Minecraft client) {
        try {
            Path logsDir = client.gameDirectory.toPath().resolve("logs").resolve("ride-paths");
            Files.createDirectories(logsDir);

            String safeName = exp.name().replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
            String timestamp = LocalDateTime.now().format(TS_FMT);
            Path logFile = logsDir.resolve(safeName + "-" + timestamp + ".csv");

            writer = Files.newBufferedWriter(logFile);
            writer.write("elapsed_sec,x,y,z,yaw,pitch");
            writer.newLine();

            activeRideName = exp.name();
            startTimeMs = System.currentTimeMillis();
            tickCounter = 0;

            LOGGER.info("Started path recording for '{}' -> {}", activeRideName, logFile);

            // Write first sample immediately
            if (client.player != null) {
                writeSample(client.player);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to start path recording", e);
            writer = null;
        }
    }

    private void writeSample(LocalPlayer player) {
        if (writer == null) return;
        try {
            double elapsed = (System.currentTimeMillis() - startTimeMs) / 1000.0;
            writer.write(String.format("%.1f,%.3f,%.3f,%.3f,%.1f,%.1f",
                    elapsed,
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            LOGGER.error("Failed to write path sample", e);
            stopRecording();
        }
    }

    public void stopRecording() {
        if (writer != null) {
            try {
                double totalSec = (System.currentTimeMillis() - startTimeMs) / 1000.0;
                LOGGER.info("Stopped path recording for '{}' (total: {:.1f}s)",
                        activeRideName, totalSec);
                writer.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close path log", e);
            }
            writer = null;
            activeRideName = null;
            startTimeMs = 0;
            tickCounter = 0;
        }
    }

    /** Clean up on disconnect. */
    public void reset() {
        stopRecording();
    }
}
