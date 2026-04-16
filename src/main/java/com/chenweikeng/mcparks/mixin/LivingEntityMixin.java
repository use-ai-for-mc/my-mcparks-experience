package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.config.FullbrightMode;
import com.chenweikeng.mcparks.config.ModConfig;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(
        method = "hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z",
        at = @At("RETURN"),
        cancellable = true)
    private void fullbright$hasEffect(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect != MobEffects.NIGHT_VISION) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof LocalPlayer)) return;
        if (!isMCParksServer()) return;
        if (shouldApplyFullbright((LocalPlayer) self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "getEffect(Lnet/minecraft/world/effect/MobEffect;)Lnet/minecraft/world/effect/MobEffectInstance;",
        at = @At("RETURN"),
        cancellable = true)
    private void fullbright$getEffect(MobEffect effect, CallbackInfoReturnable<MobEffectInstance> cir) {
        if (effect != MobEffects.NIGHT_VISION) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof LocalPlayer)) return;
        if (!isMCParksServer()) return;
        if (shouldApplyFullbright((LocalPlayer) self) && cir.getReturnValue() == null) {
            cir.setReturnValue(new MobEffectInstance(MobEffects.NIGHT_VISION, -1));
        }
    }

    private static boolean shouldApplyFullbright(LocalPlayer player) {
        boolean isRiding = player.isPassenger();
        return switch (ModConfig.currentSetting.fullbrightMode) {
            case NONE -> false;
            case ONLY_WHEN_RIDING -> isRiding;
            case ONLY_WHEN_NOT_RIDING -> !isRiding;
            case ALWAYS -> true;
        };
    }

    private static boolean isMCParksServer() {
        Minecraft mc = Minecraft.getInstance();
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
