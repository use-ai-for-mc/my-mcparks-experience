package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.ParkTracker;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import com.chenweikeng.mcparks.ride.experience.RideExperienceRegistry;
import com.chenweikeng.mcparks.subtitle.SubtitleManager;
import java.util.Optional;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, CallbackInfo ci) {
        // 1. Every message updates the park/ride-id tracker first. This is a
        //    passive observer — it reads "Traveling to X in Y" but never
        //    cancels, so travel confirmations still show in chat.
        ParkTracker.getInstance().observe(message);

        // 2. Give the currently-active ride experience a chance to claim the
        //    message as a subtitle (e.g. Disneyland Railroad's [Narrator]
        //    lines). First match wins and the message is hidden from chat.
        ExperienceContext ctx = ExperienceContext.current();
        if (ctx != null) {
            for (RideExperience exp : RideExperienceRegistry.getInstance().all()) {
                if (!exp.isActive(ctx)) continue;
                Optional<String> caption = exp.captureSubtitle(message);
                if (caption.isPresent()) {
                    SubtitleManager.setCaption(caption.get());
                    ci.cancel();
                    return;
                }
            }
        }

        // 3. Fall back to the existing aqua-styled subtitle detection used by
        //    other MCParks audio cues.
        if (SubtitleManager.isSubtitleMessage(message)) {
            SubtitleManager.setCaption(message.getString());
            ci.cancel();
        }
    }
}
