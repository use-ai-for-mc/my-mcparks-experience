package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.config.ModConfig;
import com.chenweikeng.mcparks.cursor.CursorManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Shadow @Final private Minecraft minecraft;

    /**
     * Suppresses the "Press [KEY] to dismount" overlay message when the player
     * has been a passenger for a while. This prevents the message from appearing
     * repeatedly during roller coaster inversions where MCParks transfers the
     * player between entities.
     */
    @Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mcparks$onSetOverlayMessage(Component message, boolean animate, CallbackInfo ci) {
        if (!ModConfig.currentSetting.hideMountMessageOnRide) {
            return;
        }

        // Check if this is the mount.onboard message
        if (message.getContents() instanceof TranslatableContents translatable) {
            if ("mount.onboard".equals(translatable.getKey())) {
                CursorManager cursorManager = CursorManager.getInstance();
                if (cursorManager != null && cursorManager.shouldSuppressMountMessage()) {
                    ci.cancel();
                }
            }
        }
    }

    /**
     * Hides the player health bar (hearts, hunger, armor) when the option is enabled.
     */
    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void mcparks$onRenderPlayerHealth(PoseStack poseStack, CallbackInfo ci) {
        if (!isMCParksServer()) {
            return;
        }
        if (ModConfig.currentSetting.hideHealthBar) {
            ci.cancel();
        }
    }

    /**
     * Hides the vehicle/mount health bar when the option is enabled.
     */
    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void mcparks$onRenderVehicleHealth(PoseStack poseStack, CallbackInfo ci) {
        if (!isMCParksServer()) {
            return;
        }
        if (ModConfig.currentSetting.hideHealthBar) {
            ci.cancel();
        }
    }

    /**
     * Hides the entire experience bar and level number when the option is enabled.
     */
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void mcparks$onRenderExperienceBar(PoseStack poseStack, int x, CallbackInfo ci) {
        if (isMCParksServer() && ModConfig.currentSetting.hideExperienceLevel) {
            ci.cancel();
        }
    }

    /**
     * Hides the crosshair while the player is riding a vehicle on MCParks.
     */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void mcparks$onRenderCrosshair(PoseStack poseStack, CallbackInfo ci) {
        if (isMCParksServer() && minecraft.player != null && minecraft.player.isPassenger()) {
            ci.cancel();
        }
    }

    /**
     * Hides the scoreboard sidebar when the option is enabled.
     */
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void mcparks$onDisplayScoreboardSidebar(PoseStack poseStack, Objective objective, CallbackInfo ci) {
        if (isMCParksServer() && ModConfig.currentSetting.hideScoreboard) {
            ci.cancel();
        }
    }

    /**
     * Hides the hotbar when the option is enabled.
     */
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void mcparks$onRenderHotbar(float tickDelta, PoseStack poseStack, CallbackInfo ci) {
        if (isMCParksServer() && ModConfig.currentSetting.hideHotbar) {
            ci.cancel();
        }
    }

    private boolean isMCParksServer() {
        // Server list path: set when the player clicked a saved server
        var serverData = minecraft.getCurrentServer();
        if (serverData != null && serverData.ip != null
                && serverData.ip.toLowerCase().contains("mcparks")) {
            return true;
        }
        // Direct connect / --server launch arg: getCurrentServer() is null, so
        // fall back to the live connection's remote hostname.
        ClientPacketListener listener = minecraft.getConnection();
        if (listener != null) {
            Connection conn = listener.getConnection();
            if (conn != null) {
                SocketAddress addr = conn.getRemoteAddress();
                if (addr instanceof InetSocketAddress inet) {
                    String host = inet.getHostString();
                    return host != null && host.toLowerCase().contains("mcparks");
                }
            }
        }
        return false;
    }
}
