package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.config.ModConfig;
import com.chenweikeng.mcparks.ride.RideDetector;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import com.chenweikeng.mcparks.subtitle.SubtitleManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    /**
     * Hides the chat overlay rendering when the option is enabled.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mcparks$onRender(PoseStack poseStack, int tickCount, CallbackInfo ci) {
        if (ModConfig.currentSetting.hideChat) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, CallbackInfo ci) {
        String text = message.getString();

        // 0. Optionally suppress player join/leave messages:
        //    "[+] Welcome Back, <name>" and "[-] Thanks For Visiting! <name>"
        if (ModConfig.currentSetting.hideJoinLeaveMessages
                && (text.startsWith("[+] ") || text.startsWith("[-] "))) {
            ci.cancel();
            return;
        }

        // 1. If the player is on a ride with an active experience (matched at
        //    boarding time by RideDetector), let that experience try to capture
        //    the message as a subtitle. This uses the already-matched experience
        //    rather than re-evaluating isActive() with an incomplete context.
        RideDetector detector = RideDetector.getInstance();
        if (detector != null) {
            RideExperience exp = detector.getCurrentExperience();
            if (exp != null) {
                Optional<String> caption = exp.captureSubtitle(message);
                if (caption.isPresent()) {
                    SubtitleManager.setCaption(caption.get());
                    ci.cancel();
                    return;
                }
            }
        }

    }
}
