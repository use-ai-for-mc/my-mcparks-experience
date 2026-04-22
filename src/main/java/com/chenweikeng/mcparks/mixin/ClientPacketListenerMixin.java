package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.chat.CommandTreeAliaser;
import com.chenweikeng.mcparks.config.ModConfig;
import com.mojang.brigadier.CommandDispatcher;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts incoming {@code ClientboundSetTimePacket} handling so the real
 * day-time value from the server never reaches the {@link net.minecraft.client.multiplayer.ClientLevel}
 * when fullbright is active. Without this, every server time update (~1 Hz)
 * would flash the dark sky for one frame before {@code DayTimeHandler}'s
 * next-tick clamp catches up, causing the sky to blink.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    private static final long NOON = 6000L;

    @Shadow private CommandDispatcher<SharedSuggestionProvider> commands;

    /**
     * After the vanilla handler builds the Brigadier dispatcher, rename
     * top-level literals per {@link CommandTreeAliaser} so ImagineFun-style
     * {@code /msg} and {@code /w} appear in tab completion.
     */
    @Inject(method = "handleCommands", at = @At("TAIL"))
    private void mcparks$renameCommandTreeAliases(ClientboundCommandsPacket packet, CallbackInfo ci) {
        CommandTreeAliaser.applyIfEnabled(this.commands);
    }

    @ModifyArg(
        method = "handleSetTime",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;setDayTime(J)V"
        )
    )
    private long mcparks$clampDayTime(long dayTime) {
        if (mcparks$shouldForceDay()) {
            return NOON;
        }
        return dayTime;
    }

    private static boolean mcparks$shouldForceDay() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (!mcparks$isMCParksServer(mc)) return false;
        boolean riding = player.isPassenger();
        return switch (ModConfig.currentSetting.fullbrightMode) {
            case NONE -> false;
            case ONLY_WHEN_RIDING -> riding;
            case ONLY_WHEN_NOT_RIDING -> !riding;
            case ALWAYS -> true;
        };
    }

    private static boolean mcparks$isMCParksServer(Minecraft mc) {
        var serverData = mc.getCurrentServer();
        if (serverData != null && serverData.ip != null
                && serverData.ip.toLowerCase().contains("mcparks")) {
            return true;
        }
        ClientPacketListener listener = mc.getConnection();
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
