package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.chenweikeng.mcparks.skincache.ProfileTextureExtractor;
import com.chenweikeng.mcparks.skincache.ProfileTextureExtractor.SkinInfo;
import com.chenweikeng.mcparks.skincache.TextureCache;
import com.chenweikeng.mcparks.skincache.TextureRegistrar;
import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Short-circuits player head rendering when we have the skin texture cached.
 * This bypasses the async resolution chain for faster rendering.
 */
@Mixin(SkullBlockRenderer.class)
public abstract class SkinCacheSkullBlockRendererMixin {

    /**
     * Intercept getRenderType to use our cached textures directly.
     * Method signature: getRenderType(SkullBlock.Type, GameProfile) -> RenderType
     */
    @Inject(
        method = "getRenderType(Lnet/minecraft/world/level/block/SkullBlock$Type;Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/client/renderer/RenderType;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void mcparks$onGetRenderType(
            SkullBlock.Type type,
            @Nullable GameProfile profile,
            CallbackInfoReturnable<RenderType> cir) {

        // Only handle player skulls with a profile
        if (type != SkullBlock.Types.PLAYER || profile == null) return;

        // Extract texture info from profile properties (no network call)
        SkinInfo skinInfo = ProfileTextureExtractor.extract(profile);
        if (skinInfo == null) return;

        // Check if texture is in our cache
        if (!TextureCache.isCached(skinInfo.textureUrl())) return;

        // Create ResourceLocation for the texture
        ResourceLocation textureId = new ResourceLocation(skinInfo.textureIdPath());

        // Ensure texture is registered with TextureManager
        if (!TextureRegistrar.ensureRegistered(textureId, skinInfo.textureUrl())) return;

        // Short-circuit: return RenderType directly without async resolution
        MCParksExperienceClient.LOGGER.debug("[SkinCache] Short-circuit skull render for {}", skinInfo.textureHash());
        cir.setReturnValue(RenderType.entityTranslucent(textureId));
    }
}
