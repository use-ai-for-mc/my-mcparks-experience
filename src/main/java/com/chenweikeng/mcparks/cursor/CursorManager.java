package com.chenweikeng.mcparks.cursor;

import com.chenweikeng.mcparks.config.ModConfig;
import net.minecraft.client.Minecraft;

public class CursorManager {
    private static final int MOUNT_MESSAGE_SUPPRESS_TICKS = 40; // 2 seconds

    private static CursorManager instance;

    private boolean wasPassenger = false;
    private int passengerTicks = 0;

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

        // Track how long the player has been a passenger
        if (isPassenger) {
            passengerTicks++;
        } else {
            passengerTicks = 0;
        }

        if (!ModConfig.currentSetting.cursorReleaseOnRide) {
            wasPassenger = isPassenger;
            return;
        }

        // Detect mount: release cursor
        if (!wasPassenger && isPassenger) {
            client.mouseHandler.releaseMouse();
            state.setAutomaticallyReleasedCursor(true);
        }

        // Detect dismount: re-grab cursor
        if (wasPassenger && !isPassenger) {
            state.setAutomaticallyReleasedCursor(false);
            if (client.screen == null) {
                client.mouseHandler.grabMouse();
            }
        }

        // Right-click override: Minecraft re-grabs the mouse on right-click,
        // so we release it again while the player is a passenger.
        if (isPassenger && client.mouseHandler.isRightPressed() && client.screen == null) {
            client.mouseHandler.releaseMouse();
        }

        wasPassenger = isPassenger;
    }

    public void reset() {
        wasPassenger = false;
        passengerTicks = 0;
        CursorState.getInstance().reset();
    }
}
