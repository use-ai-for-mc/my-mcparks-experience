package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.ServerState;
import com.chenweikeng.mcparks.cinematic.CinematicCameraManager;
import com.chenweikeng.mcparks.config.ModConfig;
import net.minecraft.client.player.LocalPlayer;
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
        if (!ServerState.isTargetServer()) return;
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
        if (!ServerState.isTargetServer()) return;
        if (shouldApplyFullbright((LocalPlayer) self) && cir.getReturnValue() == null) {
            cir.setReturnValue(new MobEffectInstance(MobEffects.NIGHT_VISION, -1));
        }
    }

    private static boolean shouldApplyFullbright(LocalPlayer player) {
        // Cinematic viewpoint is night-themed (fireworks), so suppress fullbright
        // while the cinematic camera is active regardless of mode.
        if (CinematicCameraManager.getInstance().isActive()) {
            return false;
        }
        boolean isRiding = player.isPassenger();
        return switch (ModConfig.currentSetting.fullbrightMode) {
            case NONE -> false;
            case ONLY_WHEN_RIDING -> isRiding;
            case ONLY_WHEN_NOT_RIDING -> !isRiding;
            case ALWAYS -> true;
        };
    }
}
