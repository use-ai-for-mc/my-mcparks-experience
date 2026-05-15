package com.chenweikeng.mcparks.firework;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * Firework location identified by a fixed sign block whose four text lines
 * match a recorded expectation. Useful for parks that don't have a Walt
 * statue viewing spot (e.g. EPCOT's World Showcase Lagoon).
 *
 * <p>Two-condition match: player within {@link #PROXIMITY_RADIUS} of the
 * sign block (cheap filter) and the sign's four-line text matches exactly
 * (authoritative check — rules out coincidental same-coord blocks on other
 * servers or rewrites of the sign by a build team).
 */
public final class SignFireworkLocation implements FireworkLocation {

    private static final double PROXIMITY_RADIUS = 48.0;
    private static final double PROXIMITY_RADIUS_SQ = PROXIMITY_RADIUS * PROXIMITY_RADIUS;

    private final String id;
    private final String displayName;
    private final BlockPos signPos;
    /** Exactly four entries, index-matched to {@link SignBlockEntity#getMessage(int, boolean)}. */
    private final List<String> expectedLines;

    public SignFireworkLocation(String id, String displayName,
                                int bx, int by, int bz,
                                List<String> expectedLines) {
        if (expectedLines.size() != 4) {
            throw new IllegalArgumentException(
                "SignFireworkLocation " + id + " needs exactly 4 expected lines, got " + expectedLines.size());
        }
        this.id = id;
        this.displayName = displayName;
        this.signPos = new BlockPos(bx, by, bz);
        this.expectedLines = List.copyOf(expectedLines);
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }

    @Override
    public boolean isHereNow(Minecraft mc) {
        if (mc == null) return false;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;

        double dx = player.getX() - (signPos.getX() + 0.5);
        double dy = player.getY() - (signPos.getY() + 0.5);
        double dz = player.getZ() - (signPos.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > PROXIMITY_RADIUS_SQ) return false;

        BlockEntity be = level.getBlockEntity(signPos);
        if (!(be instanceof SignBlockEntity sign)) return false;

        for (int i = 0; i < 4; i++) {
            Component msg = sign.getMessage(i, false);
            String actual = msg == null ? "" : msg.getString();
            if (!expectedLines.get(i).equals(actual)) return false;
        }
        return true;
    }
}
