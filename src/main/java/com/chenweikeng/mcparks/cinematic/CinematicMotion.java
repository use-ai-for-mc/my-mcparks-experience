package com.chenweikeng.mcparks.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * WASD + space/shift translated into a velocity vector on the cinematic camera.
 * Adapted from MinecraftFreecam/Freecam (MIT) — see {@link CinematicCamera} header.
 */
final class CinematicMotion {
    private static final double DIAGONAL_MULTIPLIER = Mth.sin((float) Math.toRadians(45));

    private CinematicMotion() {}

    static void doMotion(CinematicCamera cam, double hSpeed, double vSpeed) {
        float yaw = cam.getYRot();
        double vx = 0.0, vy = 0.0, vz = 0.0;

        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 side = Vec3.directionFromRotation(0, yaw + 90);

        cam.input.tick(false, 0.3F);

        boolean straight = false;
        if (cam.input.up)   { vx += forward.x * hSpeed; vz += forward.z * hSpeed; straight = true; }
        if (cam.input.down) { vx -= forward.x * hSpeed; vz -= forward.z * hSpeed; straight = true; }

        boolean strafing = false;
        if (cam.input.right) { vz += side.z * hSpeed; vx += side.x * hSpeed; strafing = true; }
        if (cam.input.left)  { vz -= side.z * hSpeed; vx -= side.x * hSpeed; strafing = true; }

        if (straight && strafing) {
            vx *= DIAGONAL_MULTIPLIER;
            vz *= DIAGONAL_MULTIPLIER;
        }

        if (cam.input.jumping)      vy += vSpeed;
        if (cam.input.shiftKeyDown) vy -= vSpeed;

        cam.setDeltaMovement(vx, vy, vz);
    }
}
