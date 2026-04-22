package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.chenweikeng.mcparks.chat.ChatColorRewriter;
import com.chenweikeng.mcparks.config.ModConfig;
import com.chenweikeng.mcparks.emoji.EmojiTransformer;
import com.chenweikeng.mcparks.ride.RideDetector;
import com.chenweikeng.mcparks.ride.RideSessionRecorder;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import com.chenweikeng.mcparks.subtitle.SubtitleManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    /**
     * Hides the chat overlay rendering when the option is enabled.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mcparks$onRender(PoseStack poseStack, int tickCount, CallbackInfo ci) {
        if (ModConfig.currentSetting.hideChat) {
            // Show chat when the player is typing (ChatScreen is open)
            if (Minecraft.getInstance().screen instanceof ChatScreen) {
                return;
            }
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, CallbackInfo ci) {
        String text = message.getString();

        boolean capturedAsSubtitle = false;
        boolean cancelled = false;

        // 0. Optionally suppress player join/leave messages:
        //    "[+] Welcome Back, <name>" and "[-] Thanks For Visiting! <name>"
        if (ModConfig.currentSetting.hideJoinLeaveMessages
                && (text.startsWith("[+] ") || text.startsWith("[-] "))) {
            cancelled = true;
        }

        // 1. If the player is on a ride with an active experience (matched at
        //    boarding time by RideDetector), let that experience try to capture
        //    the message as a subtitle. This uses the already-matched experience
        //    rather than re-evaluating isActive() with an incomplete context.
        if (!cancelled) {
            RideDetector detector = RideDetector.getInstance();
            if (detector != null) {
                RideExperience exp = detector.getCurrentExperience();
                if (exp != null) {
                    Optional<String> caption = exp.captureSubtitle(message);
                    if (caption.isPresent()) {
                        // If this ride uses timed (audio-synced) subtitles, the
                        // TimedSubtitlePlayer handles display. We still cancel
                        // the message from chat to keep the chat pane clean.
                        if (exp.subtitleResource() == null) {
                            SubtitleManager.setCaption(caption.get());
                        }
                        capturedAsSubtitle = true;
                        cancelled = true;
                    }
                }
            }
        }

        // 2. Record the message to the session recorder (before cancelling)
        RideSessionRecorder recorder = MCParksExperienceClient.getSessionRecorder();
        if (recorder != null && recorder.isRecording()) {
            recorder.onChatMessage(message, capturedAsSubtitle, cancelled);
        }

        // 3. Cancel if needed
        if (cancelled) {
            ci.cancel();
        }
    }

    /**
     * Rewrites the received message so that ":shortcode:" sequences and
     * literal Unicode emoji are replaced by PUA characters styled with the
     * {@code my-mcparks-experience:emoji} bitmap font. Declared after the
     * {@link #onAddMessage(Component, CallbackInfo)} injection so the
     * subtitle-capture path still sees the original, unmodified message.
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component mcparks$emojifyAddMessage(Component original) {
        Component whitened = ChatColorRewriter.rewrite(original);
        return EmojiTransformer.transform(whitened);
    }
}
