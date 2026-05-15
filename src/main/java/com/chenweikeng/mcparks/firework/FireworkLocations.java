package com.chenweikeng.mcparks.firework;

import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Registry of known firework-viewing locations across the MCParks world.
 *
 * <p>Each constant is a named {@link FireworkLocation} that decides for
 * itself how it recognises the player is present (Walt statue, sign block,
 * etc.). Downstream park-specific logic should reference these by name
 * (e.g. {@link #MAGIC_KINGDOM}) rather than iterating the list.
 *
 * <p>Use {@link #current()} when you only need "which firework spot am I
 * at, if any?" and don't care about the specific one.
 */
public final class FireworkLocations {

    // Walt-statue-based: four parks where the viewing spot is the statue
    // in front of the central castle. Coordinates captured in-game by
    // scanning nearby armor stands.
    public static final FireworkLocation MAGIC_KINGDOM = new WaltStatueFireworkLocation(
        "magic_kingdom",
        "Magic Kingdom Castle (Walt Disney World)",
        -247.0, 55.0, 759.5,
        "walt_statue_head.png");

    public static final FireworkLocation DISNEYLAND_CALIFORNIA = new WaltStatueFireworkLocation(
        "disneyland_california",
        "Disneyland Castle (California)",
        0.0, 63.5, -333.5,
        "walt_statue_head.png");

    public static final FireworkLocation TOKYO_DISNEYLAND = new WaltStatueFireworkLocation(
        "tokyo_disneyland",
        "Tokyo Disneyland Castle",
        -1487.70, 40.0, -1691.39,
        "walt_statue_head.png");

    public static final FireworkLocation DISNEY_ADVENTURE_WORLD_PARIS = new WaltStatueFireworkLocation(
        "disney_adventure_world_paris",
        "Disney Adventure World Castle (Paris)",
        -10205.30, 35.95, 10158.80,
        "walt_statue_head.png");

    // Sign-based: EPCOT has no Walt-statue viewing spot; the canonical
    // firework-viewing location is the World Showcase Lagoon, fingerprinted
    // by the "[Disposal] World Showcase" wall sign posted there.
    public static final FireworkLocation EPCOT_WORLD_SHOWCASE = new SignFireworkLocation(
        "epcot_world_showcase",
        "EPCOT World Showcase Lagoon",
        2869, 53, 5990,
        List.of("[Disposal]", "", "World", "Showcase"));

    private static final List<FireworkLocation> ALL = List.of(
        MAGIC_KINGDOM,
        DISNEYLAND_CALIFORNIA,
        TOKYO_DISNEYLAND,
        DISNEY_ADVENTURE_WORLD_PARIS,
        EPCOT_WORLD_SHOWCASE);

    private FireworkLocations() {}

    /** Immutable view of every registered firework location. */
    public static List<FireworkLocation> all() {
        return ALL;
    }

    /**
     * @return the firework location the player is currently at, or
     *         {@code null} if none match. First-match wins; registration
     *         order in {@link #ALL} is the tie-break.
     */
    public static FireworkLocation current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        for (FireworkLocation loc : ALL) {
            if (loc.isHereNow(mc)) return loc;
        }
        return null;
    }
}
