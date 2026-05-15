package com.chenweikeng.mcparks.firework;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Firework location identified by a Walt statue prop — an {@link ArmorStand}
 * with a {@link net.minecraft.world.item.PlayerHeadItem} in its HEAD slot
 * whose display name matches {@link #helmetName}, standing within
 * {@link #STATUE_SCAN_RADIUS} of the recorded world coordinate.
 *
 * <p>Two-condition match: player within {@link #PROXIMITY_RADIUS} of the
 * walt coord (cheap filter) and the statue is present at its recorded spot
 * (authoritative check).
 */
public final class WaltStatueFireworkLocation implements FireworkLocation {

    private static final double PROXIMITY_RADIUS = 32.0;
    private static final double PROXIMITY_RADIUS_SQ = PROXIMITY_RADIUS * PROXIMITY_RADIUS;
    private static final double STATUE_SCAN_RADIUS = 3.0;

    private final String id;
    private final String displayName;
    private final double waltX;
    private final double waltY;
    private final double waltZ;
    private final String helmetName;

    public WaltStatueFireworkLocation(String id, String displayName,
                                      double waltX, double waltY, double waltZ,
                                      String helmetName) {
        this.id = id;
        this.displayName = displayName;
        this.waltX = waltX;
        this.waltY = waltY;
        this.waltZ = waltZ;
        this.helmetName = helmetName;
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }

    @Override
    public boolean isHereNow(Minecraft mc) {
        if (mc == null) return false;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;

        double dx = player.getX() - waltX;
        double dy = player.getY() - waltY;
        double dz = player.getZ() - waltZ;
        if (dx * dx + dy * dy + dz * dz > PROXIMITY_RADIUS_SQ) return false;

        AABB box = new AABB(
            waltX - STATUE_SCAN_RADIUS, waltY - STATUE_SCAN_RADIUS, waltZ - STATUE_SCAN_RADIUS,
            waltX + STATUE_SCAN_RADIUS, waltY + STATUE_SCAN_RADIUS, waltZ + STATUE_SCAN_RADIUS);
        List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, box, e -> true);
        for (ArmorStand stand : stands) {
            ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
            if (helmet.isEmpty()) continue;
            if (helmetName.equals(helmet.getHoverName().getString())) return true;
        }
        return false;
    }
}
