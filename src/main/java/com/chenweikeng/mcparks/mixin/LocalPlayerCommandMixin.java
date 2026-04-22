package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.chat.CommandAliases;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites outgoing slash-commands from ImagineFun aliases to the
 * MCParks wire form (see {@link CommandAliases}). Target is the private
 * {@code LocalPlayer.sendCommand(MessageSigner, String, Component)} — the
 * funnel for both the 1-arg and 2-arg public {@code command} overloads, so
 * both keyboard-triggered chat input and programmatic calls are covered.
 *
 * <p>The command string is arg index 2 (slot index: 0=this, 1=signer,
 * 2=command string, 3=preview Component).
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerCommandMixin {
    @ModifyVariable(
        method = "sendCommand(Lnet/minecraft/network/chat/MessageSigner;Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private String mcparks$aliasCommand(String original) {
        return CommandAliases.rewriteOutgoing(original);
    }
}
