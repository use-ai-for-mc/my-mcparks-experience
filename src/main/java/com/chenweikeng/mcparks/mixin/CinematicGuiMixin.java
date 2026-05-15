package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.cinematic.CinematicCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps HUD elements (health, hunger, XP) bound to the real player instead of the
 * cinematic camera entity, which otherwise would report zero/default stats.
 */
@Mixin(Gui.class)
public class CinematicGuiMixin {

    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
    private void mcparks$cinematic_useRealPlayer(CallbackInfoReturnable<Player> cir) {
        if (CinematicCameraManager.getInstance().isActive()) {
            cir.setReturnValue(Minecraft.getInstance().player);
        }
    }
}
