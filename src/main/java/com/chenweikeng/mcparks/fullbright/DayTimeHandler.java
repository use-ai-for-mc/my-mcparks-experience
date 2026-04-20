package com.chenweikeng.mcparks.fullbright;

import com.chenweikeng.mcparks.config.FullbrightMode;
import com.chenweikeng.mcparks.config.ModConfig;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;

public class DayTimeHandler {
    private static final long NOON = 6000L;

    public void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }

        if (!isMCParksServer(client)) {
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

    private static boolean isMCParksServer(Minecraft client) {
        var serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null
                && serverData.ip.toLowerCase().contains("mcparks")) {
            return true;
        }
        ClientPacketListener listener = client.getConnection();
        if (listener != null) {
            Connection conn = listener.getConnection();
            if (conn != null) {
                SocketAddress addr = conn.getRemoteAddress();
                if (addr instanceof InetSocketAddress inet) {
                    String host = inet.getHostString();
                    return host != null && host.toLowerCase().contains("mcparks");
                }
            }
        }
        return false;
    }
}
