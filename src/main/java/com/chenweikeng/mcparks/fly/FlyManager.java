package com.chenweikeng.mcparks.fly;

import com.chenweikeng.mcparks.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksFly");
    private static final float DEFAULT_WALK_SPEED = 0.1f;
    private static final int FLY_COMMAND_COOLDOWN = 600; // 30 seconds between attempts
    private static final int JOIN_DELAY = 100; // 5 seconds after join before first attempt

    private int cooldown = JOIN_DELAY;

    public void reset() {
        cooldown = JOIN_DELAY;
    }

    public void tick(Minecraft client) {
        if (client.player == null) return;

        LocalPlayer player = client.player;

        if (cooldown > 0) {
            cooldown--;
        }

        // If auto-fly is on and player can't fly, send /fly
        if (ModConfig.currentSetting.autoFly && !player.getAbilities().mayfly && cooldown == 0) {
            LOGGER.info("Player cannot fly, sending /fly");
            player.chat("/fly");
            cooldown = FLY_COMMAND_COOLDOWN;
        }

        // Speed boost when fly is enabled
        applySpeedBoost(player);
    }

    private void applySpeedBoost(LocalPlayer player) {
        float multiplier = ModConfig.currentSetting.speedMultiplier;
        if (multiplier > 1.0f && player.getAbilities().mayfly) {
            player.getAbilities().setWalkingSpeed(DEFAULT_WALK_SPEED * multiplier);
        }
    }
}
