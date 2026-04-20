package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.config.ModConfig;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Read-side clamp that makes {@link Level#getDayTime()} return noon when
 * fullbright is active on the MCParks client level. This runs on every
 * reader — vanilla sky renderer, shader-pack uniforms (Iris/Oculus read
 * via this method), anything that samples day time for lighting — so the
 * sky cannot blink even if a night-time value briefly ends up stored.
 *
 * <p>Acts as a defence-in-depth layer over
 * {@link ClientPacketListenerMixin}'s packet-side clamp: if a server time
 * update ever slips through, readers still see NOON.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    private static final long NOON = 6000L;

    @Inject(method = "getDayTime()J", at = @At("HEAD"), cancellable = true)
    private void mcparks$clampDayTime(CallbackInfoReturnable<Long> cir) {
        // Only clamp for the client's rendered level — never for server
        // level instances (integrated server, villagers, mobs, etc.).
        if (!(((Object) this) instanceof ClientLevel)) return;
        if (!mcparks$shouldForceDay()) return;
        cir.setReturnValue(NOON);
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
