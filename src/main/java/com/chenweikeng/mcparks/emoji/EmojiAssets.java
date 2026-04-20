package com.chenweikeng.mcparks.emoji;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the Twemoji shortcode + unicode maps from JAR resources and exposes
 * the PUA-mapped emoji font identifier. Called once from the client
 * initializer; after init the maps are read-only.
 */
public final class EmojiAssets {
    private static final Logger LOGGER = LoggerFactory.getLogger("my-mcparks-experience/emoji");

    public static final ResourceLocation EMOJI_FONT =
            new ResourceLocation("my-mcparks-experience", "emoji");

    private static final String SHORTCODES_RES = "assets/my-mcparks-experience/twemoji/shortcodes.json";
    private static final String UNICODE_RES    = "assets/my-mcparks-experience/twemoji/unicode.json";

    /** Shortcode (without colons) -> single-char PUA string. */
    public static Map<String, String> SHORTCODES = Map.of();
    /** Unicode emoji (e.g. "\uD83D\uDE00") -> single-char PUA string. */
    public static Map<String, String> UNICODE_MAP = Map.of();

    private EmojiAssets() {}

    public static void load() {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        SHORTCODES  = loadJson(SHORTCODES_RES, gson, type);
        UNICODE_MAP = loadJson(UNICODE_RES, gson, type);
        LOGGER.info("Loaded Twemoji assets: {} shortcodes, {} unicode mappings",
                SHORTCODES.size(), UNICODE_MAP.size());
    }

    private static Map<String, String> loadJson(String resource, Gson gson, Type type) {
        ClassLoader cl = EmojiAssets.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                LOGGER.warn("Missing emoji resource: {}", resource);
                return Map.of();
            }
            try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Map<String, String> map = gson.fromJson(r, type);
                return map != null ? map : Map.of();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load emoji resource {}: {}", resource, e.toString());
            return Map.of();
        }
    }
}
