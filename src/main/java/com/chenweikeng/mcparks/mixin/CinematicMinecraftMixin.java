package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.cinematic.CinematicCameraManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the cinematic camera is active, blocks interaction that would otherwise
 * originate from the camera's viewpoint: left-click attacks, middle-click pick,
 * and held-left-click block breaking. Also tears the feature down cleanly on
 * world change / disconnect.
 */
@Mixin(Minecraft.class)
public class CinematicMinecraftMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void mcparks$cinematic_cancelAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CinematicCameraManager.getInstance().isActive()) {
            cir.cancel();
        }
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void mcparks$cinematic_cancelPick(CallbackInfo ci) {
        if (CinematicCameraManager.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void mcparks$cinematic_cancelContinueAttack(CallbackInfo ci) {
        if (CinematicCameraManager.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "clearLevel()V", at = @At("HEAD"))
    private void mcparks$cinematic_onClearLevel(CallbackInfo ci) {
        CinematicCameraManager.getInstance().onDisconnect();
    }
}
