package com.chenweikeng.mcparks.skincache;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Ensures a skin texture is registered with TextureManager (GPU-uploaded) before
 * returning a RenderType referencing it. Used by render-path mixins.
 */
public final class TextureRegistrar {

    private static final ConcurrentHashMap<String, Boolean> registered = new ConcurrentHashMap<>();

    private TextureRegistrar() {}

    /**
     * Ensure the texture is registered with TextureManager.
     * Returns true if the texture is ready to render, false if registration failed.
     */
    public static boolean ensureRegistered(ResourceLocation textureId, String textureUrl) {
        String idStr = textureId.toString();

        // Fast path: already registered by us
        if (registered.containsKey(idStr)) return true;

        TextureManager textureManager = Minecraft.getInstance().getTextureManager();

        // Get PNG from our cache
        Optional<Path> cachedPng = TextureCache.get(textureUrl);
        if (cachedPng.isEmpty()) return false;

        try {
            byte[] pngData = Files.readAllBytes(cachedPng.get());
            NativeImage image = NativeImage.read(new ByteArrayInputStream(pngData));
            image = processLegacySkinIfNeeded(image);
            DynamicTexture texture = new DynamicTexture(image);
            textureManager.register(textureId, texture);
            registered.put(idStr, Boolean.TRUE);
            MCParksExperienceClient.LOGGER.debug("[SkinCache] Registered texture {}", idStr);
            return true;
        } catch (Exception e) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Failed to register texture {}: {}", idStr, e.getMessage());
            return false;
        }
    }

    /**
     * Process legacy 64x32 skins to 64x64 format.
     * This mirrors vanilla Minecraft's legacy skin processing.
     */
    private static NativeImage processLegacySkinIfNeeded(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w == 64 && h == 32) {
            NativeImage newImage = new NativeImage(64, 64, true);
            newImage.copyFrom(image);
            image.close();
            image = newImage;
            // Clear bottom half
            image.fillRect(0, 32, 64, 32, 0);
            // Copy arm/leg textures to bottom half (same as vanilla HttpTexture.c())
            image.copyRect(4, 16, 16, 32, 4, 4, true, false);
            image.copyRect(8, 16, 16, 32, 4, 4, true, false);
            image.copyRect(0, 20, 24, 32, 4, 12, true, false);
            image.copyRect(4, 20, 16, 32, 4, 12, true, false);
            image.copyRect(8, 20, 8, 32, 4, 12, true, false);
            image.copyRect(12, 20, 16, 32, 4, 12, true, false);
            image.copyRect(44, 16, -8, 32, 4, 4, true, false);
            image.copyRect(48, 16, -8, 32, 4, 4, true, false);
            image.copyRect(40, 20, 0, 32, 4, 12, true, false);
            image.copyRect(44, 20, -8, 32, 4, 12, true, false);
            image.copyRect(48, 20, -16, 32, 4, 12, true, false);
            image.copyRect(52, 20, -8, 32, 4, 12, true, false);
        }
        return image;
    }

    /**
     * Clear registration tracking (e.g., on world change).
     */
    public static void clear() {
        registered.clear();
    }
}
