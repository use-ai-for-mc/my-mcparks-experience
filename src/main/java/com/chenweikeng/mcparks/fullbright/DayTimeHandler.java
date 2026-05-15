package com.chenweikeng.mcparks.fullbright;

import com.chenweikeng.mcparks.ServerState;
import com.chenweikeng.mcparks.cinematic.CinematicCameraManager;
import com.chenweikeng.mcparks.config.FullbrightMode;
import com.chenweikeng.mcparks.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public class DayTimeHandler {
    private static final long NOON = 6000L;

    public void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }

        if (!ServerState.isTargetServer()) {
            return;
        }

        // Cinematic viewpoint is night-themed (fireworks), so do not force noon
        // while the cinematic camera is active.
        if (CinematicCameraManager.getInstance().isActive()) {
            return;
        }

        LocalPlayer player = client.player;
        boolean isRiding = player != null && player.isPassenger();

        FullbrightMode mode = ModConfig.currentSetting.fullbrightMode;
        boolean shouldApply = switch (mode) {
            case NONE -> false;
            case ONLY_WHEN_RIDING -> isRiding;
            case ONLY_WHEN_NOT_RIDING -> !isRiding;
            case ALWAYS -> true;
        };

        if (!shouldApply) {
            return;
        }

        // Unconditional snap: any non-noon value (morning, evening, night)
        // is reset to NOON so the stored state doesn't drift between the
        // packet-side and render-side clamps.
        if (level.getDayTime() != NOON) {
            level.getLevelData().setDayTime(NOON);
        }
    }
}
