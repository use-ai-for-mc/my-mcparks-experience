package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.cinematic.CinematicCamera;
import com.chenweikeng.mcparks.cinematic.CinematicCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects the real player's mouse-look deltas to the cinematic camera entity,
 * so moving the mouse rotates the camera instead of the player's head while the
 * cinematic view is active.
 *
 * <p>Also cancels push interactions between the real player and the camera entity
 * (the camera lives in the client world and would otherwise bump physics bodies).
 *
 * <p>Adapted from MinecraftFreecam/Freecam 1.19.2 branch (MIT).
 */
@Mixin(Entity.class)
public class CinematicEntityMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void mcparks$cinematic_onTurn(double dx, double dy, CallbackInfo ci) {
        CinematicCameraManager mgr = CinematicCameraManager.getInstance();
        if (!mgr.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!this.equals(mc.player)) return;
        CinematicCamera cam = mgr.getCamera();
        if (cam != null) {
            cam.turn(dx, dy);
        }
        ci.cancel();
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void mcparks$cinematic_onPush(Entity other, CallbackInfo ci) {
        CinematicCamera cam = CinematicCameraManager.getInstance().getCamera();
        if (cam == null) return;
        Entity self = (Entity) (Object) this;
        if (other == cam || self == cam) {
            ci.cancel();
        }
    }
}
