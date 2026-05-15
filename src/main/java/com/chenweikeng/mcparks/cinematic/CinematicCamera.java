package com.chenweikeng.mcparks.cinematic;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Client-only dummy player that drives the detached cinematic camera. It lives in the
 * client world but never communicates with the server (its ClientPacketListener is a
 * no-op). Motion comes from {@link CinematicMotion} reading the keyboard; rotation
 * comes from the mouse via {@code CinematicEntityMixin} forwarding {@code Entity#turn}.
 *
 * <p>Position is hard-clamped to {@link Viewpoint#bounds} in {@link #aiStep()} so the
 * user can only pan within the preset's small box.
 *
 * <p>Adapted from MinecraftFreecam/Freecam 1.19.2 branch (MIT).
 * https://github.com/MinecraftFreecam/Freecam
 */
public final class CinematicCamera extends LocalPlayer {

    private static final float H_SPEED = 0.15f;
    private static final float V_SPEED = 0.10f;

    private final Viewpoint viewpoint;

    private static ClientPacketListener silentNetwork(Minecraft mc) {
        return new ClientPacketListener(
                mc,
                mc.screen,
                mc.getConnection().getConnection(),
                new GameProfile(UUID.randomUUID(), "CinematicCamera"),
                mc.createTelemetryManager()
        ) {
            @Override
            public void send(Packet<?> packet) {
                // Never send packets to the server. This entity exists client-side only.
            }
        };
    }

    public CinematicCamera(Minecraft mc, int id, Viewpoint viewpoint) {
        super(mc, mc.level, silentNetwork(mc), mc.player.getStats(), mc.player.getRecipeBook(), false, false);
        this.viewpoint = viewpoint;
        setId(id);
        setPose(Pose.SWIMMING);
        getAbilities().flying = true;
        input = new KeyboardInput(mc.options);

        moveTo(viewpoint.origin().x, viewpoint.origin().y, viewpoint.origin().z,
                viewpoint.yaw(), viewpoint.pitch());
        xBob = getXRot();
        yBob = getYRot();
        xBobO = xBob;
        yBobO = yBob;
    }

    public Viewpoint viewpoint() {
        return viewpoint;
    }

    public void spawn() {
        if (clientLevel != null) {
            clientLevel.putNonPlayerEntity(getId(), this);
        }
    }

    public void despawn() {
        if (clientLevel != null && clientLevel.getEntity(getId()) != null) {
            clientLevel.removeEntity(getId(), RemovalReason.DISCARDED);
        }
    }

    @Override
    public void aiStep() {
        getAbilities().setFlyingSpeed(0);
        CinematicMotion.doMotion(this, H_SPEED, V_SPEED);
        super.aiStep();
        getAbilities().flying = true;
        setOnGround(false);

        // Clamp to preset bounding box. Happens after super.aiStep() applied the
        // velocity + collision, so walls inside the box still block normally.
        Vec3 clamped = viewpoint.clamp(position());
        if (clamped.x != getX() || clamped.y != getY() || clamped.z != getZ()) {
            setPos(clamped.x, clamped.y, clamped.z);
        }
    }

    // --- silence player-ish side effects ---

    @Override
    protected void checkFallDamage(double dy, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean onClimbable() { return false; }

    @Override
    public boolean isInWater() { return false; }

    @Override
    public boolean canCollideWith(Entity other) { return false; }

    @Override
    public void setPose(Pose pose) { super.setPose(Pose.SWIMMING); }

    @Override
    public boolean isMovingSlowly() { return false; }

    @Override
    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid(FluidTags.WATER);
        return this.wasUnderwater;
    }

    @Override
    protected void doWaterSplashEffect() {}
}
