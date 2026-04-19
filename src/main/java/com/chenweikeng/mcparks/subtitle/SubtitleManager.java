package com.chenweikeng.mcparks.subtitle;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class SubtitleManager {
    private static final int AQUA_COLOR = 0x55FFFF;
    private static final int DEFAULT_DISPLAY_TICKS = 100; // 5 seconds at 20 TPS
    private static final int DELAY_TICKS = 1;

    private static String currentCaption = null;
    private static String pendingCaption = null;
    private static int displayTicks = 0;
    private static int delayCounter = 0;

    /**
     * When true, a {@link com.chenweikeng.mcparks.subtitle.TimedSubtitlePlayer}
     * is driving the display. Chat-based captions are suppressed while timed
     * subtitles are active.
     */
    private static boolean timedMode = false;

    public static boolean isSubtitleMessage(Component message) {
        for (Component sibling : message.getSiblings()) {
            Style style = sibling.getStyle();
            if (style != null && style.getColor() != null) {
                if (style.getColor().getValue() == AQUA_COLOR) {
                    String text = message.getString();
                    if (!text.startsWith("[")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void setCaption(String subtitle) {
        // When a timed subtitle player is active, ignore chat-based captions
        if (timedMode) return;

        if (currentCaption == null) {
            // Nothing showing — display immediately
            currentCaption = subtitle;
            displayTicks = DEFAULT_DISPLAY_TICKS;
        } else if (pendingCaption != null) {
            // Already have a pending caption — replace it and reset timer
            pendingCaption = subtitle;
            displayTicks = DEFAULT_DISPLAY_TICKS;
        } else {
            // Currently showing something — queue the new one
            pendingCaption = subtitle;
            delayCounter = DELAY_TICKS;
            displayTicks = DEFAULT_DISPLAY_TICKS;
        }
    }

    /**
     * Set a caption from the timed subtitle player. Bypasses queuing —
     * replaces the current display immediately.
     */
    public static void setTimedCaption(String text) {
        timedMode = true;
        currentCaption = text;
        pendingCaption = null;
        displayTicks = DEFAULT_DISPLAY_TICKS;
        delayCounter = 0;
    }

    /**
     * Clear the timed caption (gap between entries). The display will
     * fade naturally via the tick countdown.
     */
    public static void clearTimedCaption() {
        currentCaption = null;
        pendingCaption = null;
        displayTicks = 0;
        delayCounter = 0;
    }

    public static void tick() {
        if (displayTicks <= 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        boolean isRiding = client.player != null && client.player.isPassenger();

        // Only count down when NOT riding
        if (!isRiding) {
            displayTicks--;
        }

        if (displayTicks <= 0) {
            currentCaption = null;
            pendingCaption = null;
            return;
        }

        // Handle pending caption transition
        if (delayCounter > 0) {
            delayCounter--;
            if (delayCounter <= 0 && pendingCaption != null) {
                currentCaption = pendingCaption;
                pendingCaption = null;
            }
        }
    }

    public static String getCurrentCaption() {
        return currentCaption;
    }

    public static int getDisplayTicks() {
        return displayTicks;
    }

    public static void clear() {
        currentCaption = null;
        pendingCaption = null;
        displayTicks = 0;
        delayCounter = 0;
        timedMode = false;
    }
}
