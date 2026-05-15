package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.cinematic.CinematicCameraManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses the hovered-block outline while the cinematic camera is active.
 * The outline would otherwise follow whatever the camera's crosshair is pointing at,
 * which is distracting for a fixed cinematic shot.
 */
@Mixin(GameRenderer.class)
public class CinematicGameRendererMixin {

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void mcparks$cinematic_hideOutline(CallbackInfoReturnable<Boolean> cir) {
        if (CinematicCameraManager.getInstance().isActive()) {
            cir.setReturnValue(false);
        }
    }
}
