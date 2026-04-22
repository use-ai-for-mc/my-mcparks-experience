package com.chenweikeng.mcparks.chat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the rank-pill map from JAR resources. Maps lowercased rank name
 * (e.g. {@code "club 33"}, {@code "cast member"}) to the single PUA
 * character that renders as the pill badge under
 * {@code my-mcparks-experience:ranks}, plus metadata for coloring the
 * username span.
 */
public final class RankBadges {
    private static final Logger LOGGER = LoggerFactory.getLogger("my-mcparks-experience/ranks");

    public static final ResourceLocation RANKS_FONT =
            new ResourceLocation("my-mcparks-experience", "ranks");

    private static final String RESOURCE = "assets/my-mcparks-experience/ranks/ranks.json";

    public static final class Entry {
        public String display;
        public String charStr;  // one-char PUA string (JSON field: "char")
        public String fill;     // "#RRGGBB"
        public String text;     // "#RRGGBB"
        public int width_px;
    }

    private static Map<String, Entry> BADGES = Collections.emptyMap();

    private RankBadges() {}

    public static void load() {
        ClassLoader cl = RankBadges.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.warn("Missing rank badges resource: {}", RESOURCE);
                return;
            }
            try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                // JSON uses "char" as a field which is a reserved word in Java; map manually.
                Type rawType = new TypeToken<Map<String, Map<String, Object>>>(){}.getType();
                Map<String, Map<String, Object>> raw = new Gson().fromJson(r, rawType);
                if (raw == null) return;
                java.util.HashMap<String, Entry> parsed = new java.util.HashMap<>();
                for (Map.Entry<String, Map<String, Object>> e : raw.entrySet()) {
                    Map<String, Object> v = e.getValue();
                    Entry entry = new Entry();
                    entry.display = (String) v.get("display");
                    entry.charStr = (String) v.get("char");
                    entry.fill    = (String) v.get("fill");
                    entry.text    = (String) v.get("text");
                    Object w = v.get("width_px");
                    entry.width_px = w instanceof Number ? ((Number) w).intValue() : 0;
                    parsed.put(e.getKey().toLowerCase(Locale.ROOT), entry);
                }
                BADGES = Collections.unmodifiableMap(parsed);
                LOGGER.info("Loaded {} rank badges", BADGES.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load rank badges: {}", e.toString());
        }
    }

    /** Look up a badge by rank label (case-insensitive, whitespace-trimmed). */
    public static Entry get(String rankLabel) {
        if (rankLabel == null) return null;
        return BADGES.get(rankLabel.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isEmpty() { return BADGES.isEmpty(); }
}
