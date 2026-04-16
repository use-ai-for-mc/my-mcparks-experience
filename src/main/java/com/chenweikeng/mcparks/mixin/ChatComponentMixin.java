package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.subtitle.SubtitleManager;
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
        if (SubtitleManager.isSubtitleMessage(message)) {
            SubtitleManager.setCaption(message.getString());
            ci.cancel(); // Suppress from chat
        }
    }
}
