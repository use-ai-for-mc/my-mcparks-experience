package com.chenweikeng.mcparks.cursor;

import com.chenweikeng.mcparks.cinematic.CinematicCameraManager;
import com.chenweikeng.mcparks.config.ModConfig;
import net.minecraft.client.Minecraft;

public class CursorManager {
    private static final int MOUNT_MESSAGE_SUPPRESS_TICKS = 40; // 2 seconds

    private static CursorManager instance;

    private boolean wasPassenger = false;
    private int passengerTicks = 0;
    private boolean wasCinematic = false;

    public CursorManager() {
        instance = this;
    }

    public static CursorManager getInstance() {
        return instance;
    }

    /**
     * Returns true if the mount message should be suppressed because
     * the player has been a passenger long enough that showing
     * "Press SHIFT to dismount" would be redundant/annoying.
     */
    public boolean shouldSuppressMountMessage() {
        return passengerTicks >= MOUNT_MESSAGE_SUPPRESS_TICKS;
    }

    public void tick(Minecraft client) {
        if (client.player == null) return;

        CursorState state = CursorState.getInstance();
        state.tickGrace();

        boolean isPassenger = client.player.isPassenger();
        boolean isCinematic = CinematicCameraManager.getInstance().isActive();

        // Track how long the player has been a passenger
        if (isPassenger) {
            passengerTicks++;
        } else {
            passengerTicks = 0;
        }

        boolean rideReleaseEnabled = ModConfig.currentSetting.cursorReleaseOnRide;
        boolean shouldReleaseRide = rideReleaseEnabled && isPassenger;
        boolean shouldRelease = shouldReleaseRide || isCinematic;
        boolean wasReleased = (rideReleaseEnabled && wasPassenger) || wasCinematic;

        // Detect entering a release state: release cursor
        if (!wasReleased && shouldRelease) {
            client.mouseHandler.releaseMouse();
            state.setAutomaticallyReleasedCursor(true);
        }

        // Detect leaving a release state: re-grab cursor
        if (wasReleased && !shouldRelease) {
            state.setAutomaticallyReleasedCursor(false);
            if (client.screen == null) {
                client.mouseHandler.grabMouse();
            }
        }

        // Right-click override: Minecraft re-grabs the mouse on right-click,
        // so we release it again while we want the cursor released.
        if (shouldRelease && client.mouseHandler.isRightPressed() && client.screen == null) {
            client.mouseHandler.releaseMouse();
        }

        wasPassenger = isPassenger;
        wasCinematic = isCinematic;
    }

    public void reset() {
        wasPassenger = false;
        passengerTicks = 0;
        wasCinematic = false;
        CursorState.getInstance().reset();
    }
}
