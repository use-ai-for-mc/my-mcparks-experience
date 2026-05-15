package com.chenweikeng.mcparks.cinematic;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A named, fixed castle-viewpoint preset. The camera starts at {@link #origin} with
 * {@link #yaw}/{@link #pitch}, and its position is hard-clamped to {@link #bounds}
 * every tick so the user can only move around within a small box.
 */
public record Viewpoint(
        String name,
        Vec3 origin,
        float yaw,
        float pitch,
        AABB bounds
) {
    /** Preset centered on the given origin with a symmetric AABB of the given half-extents. */
    public static Viewpoint centered(String name, Vec3 origin, float yaw, float pitch,
                                     double halfX, double halfY, double halfZ) {
        AABB aabb = new AABB(
                origin.x - halfX, origin.y - halfY, origin.z - halfZ,
                origin.x + halfX, origin.y + halfY, origin.z + halfZ);
        return new Viewpoint(name, origin, yaw, pitch, aabb);
    }

    public Vec3 clamp(Vec3 pos) {
        double x = Math.max(bounds.minX, Math.min(bounds.maxX, pos.x));
        double y = Math.max(bounds.minY, Math.min(bounds.maxY, pos.y));
        double z = Math.max(bounds.minZ, Math.min(bounds.maxZ, pos.z));
        return new Vec3(x, y, z);
    }
}
