package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.chenweikeng.mcparks.ServerState;
import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.chat.ChatColorRewriter;
import com.chenweikeng.mcparks.config.ModConfig;
import com.chenweikeng.mcparks.emoji.EmojiTransformer;
import com.chenweikeng.mcparks.monkeycraft.MonkeycraftCompat;
import com.chenweikeng.mcparks.ride.RideDetector;
import com.chenweikeng.mcparks.ride.RideSessionRecorder;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import com.chenweikeng.mcparks.subtitle.SubtitleManager;
import com.chenweikeng.monkeycraft_api.v1.ChatMessageResult;
import com.chenweikeng.monkeycraft_api.v1.IncomingChatContext;
import com.chenweikeng.monkeycraft_api.v1.MonkeycraftApi;
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
     * Per-thread hand-off from the INCOMING_CHAT dispatch to the subsequent
     * {@link ModifyVariable} pass: when a MonkeyCraft listener returns
     * {@code MODIFY}, we stash the replacement here so the rewriter below
     * picks it up instead of the original argument.
     */
    private static final ThreadLocal<Component> MCPARKS$MONKEYCRAFT_MODIFIED = new ThreadLocal<>();

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
        // 0a. Fire MonkeyCraft INCOMING_CHAT only when the MonkeyCraft companion
        //     mod is installed AND we are on an MCParks server. Sender uuid/name
        //     are empty because this hook is at the display layer (post-packet,
        //     post-reassembly); a richer binding would require hooking
        //     ClientPacketListener. DENY cancels rendering outright. Any
        //     setMessage mutation on the context is honored regardless of the
        //     terminal return value, so listeners that mutate then return PASS
        //     (to let later listeners chain) still have their edits picked up
        //     by the @ModifyVariable pass below.
        Component effective = message;
        if (MonkeycraftCompat.isAvailable() && ServerState.isTargetServer()) {
            try {
                IncomingChatContext ctx = new IncomingChatContext(message, "", "");
                ChatMessageResult result = MonkeycraftApi.INCOMING_CHAT.invoker().onIncomingChat(ctx);
                if (result == ChatMessageResult.DENY) {
                    ci.cancel();
                    return;
                }
                Component mutated = ctx.getMessage();
                if (mutated != null && mutated != message) {
                    effective = mutated;
                    MCPARKS$MONKEYCRAFT_MODIFIED.set(effective);
                }
            } catch (Throwable t) {
                MCParksExperienceClient.LOGGER.warn("MonkeyCraft INCOMING_CHAT listener threw", t);
            }
        }

        String text = effective.getString();

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
                    Optional<String> caption = exp.captureSubtitle(effective);
                    if (caption.isPresent()) {
                        // If this ride uses timed (audio-synced) subtitles AND
                        // PC audio is playing locally, the TimedSubtitlePlayer
                        // handles display. Otherwise (no timed resource, or
                        // audio handed off to a MonkeyCraft phone client), fall
                        // back to chat-based captions so the user still gets
                        // subtitles during a handoff.
                        if (exp.subtitleResource() == null
                                || !MCParksAudioService.getInstance().isConnected()) {
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
            recorder.onChatMessage(effective, capturedAsSubtitle, cancelled);
        }

        // 3. Cancel if needed — also clears the MonkeyCraft stash so a cancelled
        //    message does not leak its modification into a later unrelated call.
        if (cancelled) {
            MCPARKS$MONKEYCRAFT_MODIFIED.remove();
            ci.cancel();
        }
    }

    /**
     * Rewrites the received message so that ":shortcode:" sequences and
     * literal Unicode emoji are replaced by PUA characters styled with the
     * {@code my-mcparks-experience:emoji} bitmap font. Declared after the
     * {@link #onAddMessage(Component, CallbackInfo)} injection so the
     * subtitle-capture path still sees the original, unmodified message.
     *
     * Also swaps in the MonkeyCraft MODIFY replacement (if any) before
     * running the mod's own rewriters, so external listeners and local
     * transforms compose cleanly.
     */
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component mcparks$emojifyAddMessage(Component original) {
        Component stashed = MCPARKS$MONKEYCRAFT_MODIFIED.get();
        MCPARKS$MONKEYCRAFT_MODIFIED.remove();
        Component base = stashed != null ? stashed : original;
        Component whitened = ChatColorRewriter.rewrite(base);
        return EmojiTransformer.transform(whitened);
    }
}
