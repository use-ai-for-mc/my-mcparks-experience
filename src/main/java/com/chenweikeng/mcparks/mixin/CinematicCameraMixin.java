package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.cinematic.CinematicCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On the tick the camera entity swaps in/out, snap eye-height instantly to avoid a
 * nauseating vertical slide between the real player's eye height and the cinematic
 * camera's swimming-pose eye height.
 */
@Mixin(Camera.class)
public class CinematicCameraMixin {
    @Shadow private Entity entity;
    @Shadow private float eyeHeightOld;
    @Shadow private float eyeHeight;

    @Inject(method = "setup", at = @At("HEAD"))
    private void mcparks$cinematic_snapEyeHeight(BlockGetter area, Entity newFocus,
                                                 boolean thirdPerson, boolean inverse,
                                                 float tickDelta, CallbackInfo ci) {
        if (newFocus == null || this.entity == null || newFocus.equals(this.entity)) {
            return;
        }
        if (newFocus instanceof CinematicCamera || this.entity instanceof CinematicCamera) {
            this.eyeHeightOld = this.eyeHeight = newFocus.getEyeHeight();
        }
    }
}
