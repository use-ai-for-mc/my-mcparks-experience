package com.chenweikeng.mcparks.ride;

import com.chenweikeng.mcparks.subtitle.TimedSubtitlePlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;

public class RideHudRenderer extends GuiComponent {
    private static final int TIME_COLOR_NORMAL = 0x55FF55;  // Green
    private static final int TIME_COLOR_WARNING = 0xFFFF55; // Yellow (< 60s)
    private static final int TIME_COLOR_ENDING = 0xFF5555;  // Red (< 10s)

    private final RideDetector rideDetector;
    private TimedSubtitlePlayer timedSubtitlePlayer;

    public RideHudRenderer(RideDetector rideDetector) {
        this.rideDetector = rideDetector;
    }

    /**
     * Set the timed subtitle player reference so the HUD can use
     * audio-based progress estimates when available.
     */
    public void setTimedSubtitlePlayer(TimedSubtitlePlayer player) {
        this.timedSubtitlePlayer = player;
    }

    public void render(PoseStack poseStack, float tickDelta) {
        // Only render when actually on the ride — skip the preshow window
        // where a RideExperience is matched via boarding-loop audio but the
        // player hasn't boarded yet.
        if (!rideDetector.isOnRide() || !rideDetector.hasRideTime()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        String rideName = rideDetector.getRideDisplayName();
        int totalSeconds = rideDetector.getRideTimeSeconds();
        if (rideName == null || totalSeconds <= 0) {
            return;
        }

        int remainingSeconds;
        int elapsedSeconds;

        // Prefer audio-based estimate from the timed subtitle player
        if (timedSubtitlePlayer != null && timedSubtitlePlayer.isLoaded()) {
            int audioRemaining = timedSubtitlePlayer.getEstimatedRemainingSeconds();
            if (audioRemaining >= 0) {
                remainingSeconds = audioRemaining;
                elapsedSeconds = timedSubtitlePlayer.getEstimatedElapsedSeconds();
                // Use the subtitle data's total ride time for consistency
                int audioTotal = timedSubtitlePlayer.getTotalRideTimeSec();
                if (audioTotal > 0) {
                    totalSeconds = audioTotal;
                }
            } else {
                // No audio estimate yet, fall back to detector timer
                remainingSeconds = rideDetector.getRemainingSeconds();
                elapsedSeconds = rideDetector.getElapsedSeconds();
            }
        } else {
            remainingSeconds = rideDetector.getRemainingSeconds();
            elapsedSeconds = rideDetector.getElapsedSeconds();
        }

        // Calculate progress percentage
        int progress = Math.min(100, (elapsedSeconds * 100) / Math.max(1, totalSeconds));

        // Format the display text
        String timeLeft = formatDuration(remainingSeconds);
        String displayText = String.format("%s (%d%%, %s left)", rideName, progress, timeLeft);

        // Choose color based on remaining time
        int timeColor;
        if (remainingSeconds <= 10) {
            timeColor = TIME_COLOR_ENDING;
        } else if (remainingSeconds <= 60) {
            timeColor = TIME_COLOR_WARNING;
        } else {
            timeColor = TIME_COLOR_NORMAL;
        }

        // Render at top center of screen
        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int textWidth = font.width(displayText);
        int x = (screenWidth - textWidth) / 2;
        int y = 5;

        // Draw text with shadow
        font.drawShadow(poseStack, displayText, x, y, timeColor);

        // Draw progress bar below the text
        int barWidth = Math.max(textWidth, 150);
        int barHeight = 3;
        int barX = (screenWidth - barWidth) / 2;
        int barY = y + font.lineHeight + 2;

        // Background bar (dark translucent)
        fill(poseStack, barX, barY, barX + barWidth, barY + barHeight, 0x80000000);

        // Progress fill
        int fillWidth = (barWidth * progress) / 100;
        fill(poseStack, barX, barY, barX + fillWidth, barY + barHeight, 0xFF000000 | timeColor);
    }

    private String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "0 sec";
        }

        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%d sec", seconds);
        }
    }
}
