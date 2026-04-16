package com.chenweikeng.mcparks.skincache;

import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts texture URL and hash directly from a GameProfile's embedded properties,
 * without any network call.
 *
 * <p>The "textures" property in GameProfile contains a Base64-encoded JSON payload
 * with the skin URL. This is available immediately from NBT/network data.
 */
public final class ProfileTextureExtractor {

    private static final ConcurrentHashMap<String, SkinInfo> cache = new ConcurrentHashMap<>();

    private ProfileTextureExtractor() {}

    /**
     * Extract skin texture info from a GameProfile.
     * Returns null if the profile has no embedded texture data.
     * Results are cached by base64 payload for fast repeat lookups.
     */
    @Nullable
    public static SkinInfo extract(GameProfile profile) {
        if (profile == null) return null;

        Property texturesProp = Iterables.getFirst(profile.getProperties().get("textures"), null);
        if (texturesProp == null) return null;

        String b64 = texturesProp.getValue();
        SkinInfo cached = cache.get(b64);
        if (cached != null) return cached;

        try {
            String json = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) return null;

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) return null;

            String url = skin.get("url").getAsString();
            // Hash is the last path segment of the URL
            String hash = url.substring(url.lastIndexOf('/') + 1);
            // Replicate SkinManager's ID generation: "skins/" + sha1(hash)
            @SuppressWarnings("deprecation")
            String textureIdPath = "skins/" + Hashing.sha1().hashUnencodedChars(hash).toString();

            SkinInfo info = new SkinInfo(url, hash, textureIdPath);
            cache.put(b64, info);
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    public static class SkinInfo {
        private final String textureUrl;
        private final String textureHash;
        private final String textureIdPath;

        public SkinInfo(String textureUrl, String textureHash, String textureIdPath) {
            this.textureUrl = textureUrl;
            this.textureHash = textureHash;
            this.textureIdPath = textureIdPath;
        }

        public String textureUrl() {
            return textureUrl;
        }

        public String textureHash() {
            return textureHash;
        }

        public String textureIdPath() {
            return textureIdPath;
        }
    }
}
