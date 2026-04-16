package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.chenweikeng.mcparks.skincache.TextureCache;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts skin texture downloads to use our persistent cache.
 * - On load: check our cache first, skip HTTP if cached
 * - After download: store in our cache for future use
 */
@Mixin(HttpTexture.class)
public abstract class SkinCacheHttpTextureMixin {

    @Shadow @Final private String urlString;
    @Shadow @Final @Nullable private File file;
    @Shadow private boolean uploaded;

    @Unique
    private boolean mcparks$cacheChecked = false;

    /**
     * Before the vanilla load method runs, check our cache.
     * If we have the texture cached, load from our cache and copy to vanilla's cache location.
     */
    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("HEAD"))
    private void mcparks$onLoadHead(ResourceManager resourceManager, CallbackInfo ci) {
        if (mcparks$cacheChecked) return;
        mcparks$cacheChecked = true;

        // If vanilla already has a local cache file, no need to do anything
        if (file != null && file.isFile()) return;

        // Check our persistent cache
        try {
            Optional<Path> cached = TextureCache.get(urlString);
            if (cached.isPresent() && file != null) {
                // Copy from our cache to vanilla's expected location
                // This lets vanilla's normal flow handle the texture loading
                byte[] data = Files.readAllBytes(cached.get());
                file.getParentFile().mkdirs();
                Files.write(file.toPath(), data);
                MCParksExperienceClient.LOGGER.debug("[SkinCache] Restored from cache: {}", urlString);
            }
        } catch (Exception e) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Error restoring from cache", e);
        }
    }

    /**
     * After vanilla successfully loads/downloads a texture, store it in our cache.
     * This runs after the texture file has been written to disk by vanilla.
     */
    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("RETURN"))
    private void mcparks$onLoadReturn(ResourceManager resourceManager, CallbackInfo ci) {
        // Only cache if not already in our cache and vanilla wrote a file
        if (TextureCache.isCached(urlString)) return;
        if (file == null || !file.isFile()) return;

        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (TextureCache.put(urlString, data)) {
                MCParksExperienceClient.LOGGER.debug("[SkinCache] Stored in cache: {}", urlString);
            }
        } catch (Exception e) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Failed to cache texture", e);
        }
    }
}
