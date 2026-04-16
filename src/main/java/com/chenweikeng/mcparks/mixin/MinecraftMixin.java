package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.cursor.CursorState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow public net.minecraft.client.gui.screens.Screen screen;

    /**
     * Force the game to consider the window active when we have automatically released the cursor.
     * This prevents the game from pausing when focus is lost due to cursor release.
     *
     * Only applies when no screen is open - screens handle their own focus.
     */
    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void mcparks$onIsWindowActive(CallbackInfoReturnable<Boolean> cir) {
        // Don't interfere when a screen is open - let Minecraft handle focus normally
        if (screen != null) {
            return;
        }

        CursorState state = CursorState.getInstance();
        if (state.isAutomaticallyReleasedCursor() || state.isWithinWindowRestoreGrace()) {
            cir.setReturnValue(true);
        }
    }
}
