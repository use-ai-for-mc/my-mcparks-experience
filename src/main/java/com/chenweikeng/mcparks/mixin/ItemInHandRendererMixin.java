package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    /**
     * Hides the player's hands and held items while riding a vehicle,
     * for a cleaner first-person view when recording ride videos.
     */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void mcparks$onRenderHandsWithItems(
            float tickDelta, PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            LocalPlayer player, int light, CallbackInfo ci) {
        if (ModConfig.currentSetting.hidePlayerOnRide && player.isPassenger()) {
            ci.cancel();
        }
    }
}
