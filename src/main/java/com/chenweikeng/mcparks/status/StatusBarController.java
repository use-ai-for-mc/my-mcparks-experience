package com.chenweikeng.mcparks.status;

import com.chenweikeng.mcparks.ride.RideDetector;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives the native macOS status-bar helper with the remaining ride time so the
 * countdown stays visible in the menu bar while the Minecraft window is
 * minimized. Active whenever {@link RideDetector} reports the player is on a
 * ride; shows {@code M:SS} remaining when a duration is known, {@code --:--}
 * otherwise, and clears on dismount / disconnect.
 */
public final class StatusBarController {
    private static final Logger LOGGER = LoggerFactory.getLogger("StatusBarController");
    private static final StatusBarController INSTANCE = new StatusBarController();
    private static final String NO_TIMING_PLACEHOLDER = "--:--";

    public static StatusBarController getInstance() {
        return INSTANCE;
    }

    private volatile StatusBarBridge bridge;
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private volatile boolean shutdownHookRegistered;
    private String lastTextSent = "";

    private StatusBarController() {}

    public void tick(Minecraft client) {
        RideDetector detector = RideDetector.getInstance();
        boolean isRiding = detector != null && detector.isOnRide();

        if (!isRiding) {
            if (!lastTextSent.isEmpty()) {
                sendIfRunning("");
                lastTextSent = "";
            }
            return;
        }

        ensureStarted();

        String text = computeText(detector);
        if (!text.equals(lastTextSent)) {
            sendIfRunning(text);
            lastTextSent = text;
        }
    }

    public void onDisconnect() {
        lastTextSent = "";
        StatusBarBridge b = bridge;
        if (b != null && b.isRunning()) {
            b.setText("");
        }
    }

    private String computeText(RideDetector detector) {
        int remaining = detector.getRemainingSeconds();
        if (remaining < 0) {
            return NO_TIMING_PLACEHOLDER;
        }
        return formatMinutesSeconds(remaining);
    }

    private static String formatMinutesSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void sendIfRunning(String text) {
        StatusBarBridge b = bridge;
        if (b != null && b.isRunning()) {
            b.setText(text);
        }
    }

    private void ensureStarted() {
        StatusBarBridge existing = bridge;
        if (existing != null && existing.isRunning()) {
            return;
        }
        if (!starting.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                StatusBarBridge b = new StatusBarBridge();
                if (b.start()) {
                    bridge = b;
                    registerShutdownHookOnce();
                } else {
                    LOGGER.debug("Status helper failed to start; countdown disabled");
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Unexpected error starting status helper", e);
            } finally {
                starting.set(false);
            }
        }, "StatusBarController-Starter");
        t.setDaemon(true);
        t.start();
    }

    private void registerShutdownHookOnce() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            StatusBarBridge b = bridge;
            if (b != null) {
                b.stop();
            }
        }, "StatusBarController-Shutdown"));
    }
}
