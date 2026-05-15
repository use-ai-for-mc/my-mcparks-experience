package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.cinematic.CinematicCamera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the invisible cinematic camera entity from ever being rendered —
 * otherwise third-party shaders (e.g. Iris) would draw its shadow.
 */
@Mixin(EntityRenderDispatcher.class)
public class CinematicEntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void mcparks$cinematic_hideDummy(Entity entity, Frustum frustum,
                                             double x, double y, double z,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof CinematicCamera) {
            cir.setReturnValue(false);
        }
    }
}
