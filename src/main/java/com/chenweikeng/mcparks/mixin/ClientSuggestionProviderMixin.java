package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.chat.CommandAliases;
import com.chenweikeng.mcparks.chat.SuggestionOffsetTracker;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Wire the command-alias system into the tab-completion round trip:
 * <ol>
 *   <li>Rewrite the outgoing {@code ServerboundCommandSuggestionPacket}'s
 *       command string from user-form to wire-form, and record the length
 *       delta keyed by suggestion id.</li>
 *   <li>When the response arrives, shift each suggestion's
 *       {@link StringRange} back into the user's buffer frame.</li>
 * </ol>
 */
@Mixin(ClientSuggestionProvider.class)
public abstract class ClientSuggestionProviderMixin {

    @Shadow private int pendingSuggestionsId;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestionsFuture;

    @ModifyArgs(
        method = "customSuggestion(Lcom/mojang/brigadier/context/CommandContext;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ServerboundCommandSuggestionPacket;<init>(ILjava/lang/String;)V"
        )
    )
    private void mcparks$aliasSuggestionRequest(Args args) {
        if (!CommandAliases.enabled()) return;
        int id = args.get(0);
        String original = args.get(1);
        String rewritten = CommandAliases.rewriteSlash(original);
        if (rewritten.equals(original)) return;
        SuggestionOffsetTracker.record(id, rewritten.length() - original.length());
        args.set(1, rewritten);
    }

    /**
     * Shift each suggestion's string range so it matches the user's buffer
     * rather than the one we sent to the server.
     * {@code user_pos = wire_pos - delta}.
     */
    @Inject(
        method = "completeCustomSuggestions(ILcom/mojang/brigadier/suggestion/Suggestions;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mcparks$shiftSuggestionResponse(int id, Suggestions suggestions, CallbackInfo ci) {
        int delta = SuggestionOffsetTracker.consume(id);
        if (delta == 0) return;
        ci.cancel();

        Suggestions shifted = shiftRanges(suggestions, -delta);
        if (this.pendingSuggestionsId != id) return;
        if (this.pendingSuggestionsFuture != null) {
            this.pendingSuggestionsFuture.complete(shifted);
        }
        this.pendingSuggestionsFuture = null;
        this.pendingSuggestionsId = -1;
    }

    private static Suggestions shiftRanges(Suggestions suggestions, int shift) {
        StringRange oldRange = suggestions.getRange();
        int newStart = Math.max(0, oldRange.getStart() + shift);
        int newEnd   = Math.max(newStart, oldRange.getEnd() + shift);
        StringRange newRange = new StringRange(newStart, newEnd);

        List<Suggestion> out = new ArrayList<>(suggestions.getList().size());
        for (Suggestion s : suggestions.getList()) {
            StringRange sr = s.getRange();
            int sStart = Math.max(0, sr.getStart() + shift);
            int sEnd   = Math.max(sStart, sr.getEnd() + shift);
            out.add(new Suggestion(new StringRange(sStart, sEnd), s.getText(), s.getTooltip()));
        }
        return new Suggestions(newRange, out);
    }
}
