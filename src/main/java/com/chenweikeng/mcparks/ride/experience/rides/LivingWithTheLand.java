package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Living with the Land &mdash; the gentle boat ride through EPCOT's
 * greenhouses and agricultural scenes. Detected via the scoreboard ride
 * name {@code "LWTL"} or the boat vehicle model ({@code iron_axe:117}).
 *
 * <p>Vehicle: invisible armor stand with {@code iron_axe:117}
 * ({@code vehicles/wdw/lwtl} in the resource pack).
 *
 * <p>Narration is delivered as unprefixed aqua-colored ({@code §b},
 * {@code 0x55FFFF}) chat lines with no speaker prefix, e.g.
 * {@code "One of those living systems is the rainforest..."}.
 * Detected by checking the Component's style for aqua color and
 * ensuring it isn't player chat.
 *
 * <p>Both detection and subtitles are gated on {@code isPassenger} &mdash;
 * narration only plays while aboard the boat.
 */
public class LivingWithTheLand implements RideExperience {

    private static final String NAME = "Living with the Land";
    private static final String PARK = "Walt Disney World Resort";

    /** Boat: iron_axe with damage 117. */
    private static final String VEHICLE_ITEM = "iron_axe";
    private static final int VEHICLE_DAMAGE = 117;

    /** Aqua color value used by MCParks for LWTL narration. */
    private static final int AQUA = 0x55FFFF;  // §b

    /** Timed subtitle data parsed from .ass files for all 22 narration tracks. */
    private static final String SUBTITLE_RESOURCE =
            "/assets/my-mcparks-experience/subtitles/lwtl.json";

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }
    @Override public String subtitleResource() { return SUBTITLE_RESOURCE; }

    /** Full boat cycle: 12 min 56 sec. */
    @Override public int rideTimeSeconds() { return 12 * 60 + 56; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!ctx.isPassenger) return false;
        // No park gate — LWTL is unique across the entire server, and some
        // EPCOT scoreboards leave the park code blank.
        // Primary: ride name from sidebar scoreboard
        if (ctx.rideNameMatchesAny("LWTL", "Living with the Land", "Living With The Land")) return true;
        // Fallback: player is riding the boat (handles mid-session mod load)
        for (ExperienceContext.NearbyModel m : ctx.nearbyModels) {
            if (m.matches(VEHICLE_ITEM, VEHICLE_DAMAGE)) return true;
        }
        return false;
    }

    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();
        if (text.isEmpty()) return Optional.empty();

        // Aqua-colored unprefixed narration lines (skip reward/payout messages)
        if (isAquaColored(message) && !isPlayerChat(text) && !isRewardMessage(text)) {
            return Optional.of(text);
        }

        return Optional.empty();
    }

    // -- Style helpers --

    /** Check whether the Component (root or any sibling) carries aqua color. */
    private static boolean isAquaColored(Component message) {
        if (hasAquaStyle(message.getStyle())) return true;
        for (Component sibling : message.getSiblings()) {
            if (hasAquaStyle(sibling.getStyle())) return true;
        }
        return false;
    }

    private static boolean hasAquaStyle(Style style) {
        if (style == null) return false;
        if (style.getColor() == null) return false;
        return style.getColor().getValue() == AQUA;
    }

    /** Player chat lines on MCParks follow the pattern "[Role] Name › message". */
    private static boolean isPlayerChat(String text) {
        return text.contains(" \u203A ");  // › (single right-pointing angle quotation mark)
    }

    /**
     * MCParks ride-end reward messages use aqua color too, e.g.
     * "+ ₱131.0 – For riding Living with the Land",
     * "You have received ₱96.0 total!",
     * "You've ridden Living with the Land 17 times!".
     * Filter these out so they don't appear as subtitles.
     */
    private static boolean isRewardMessage(String text) {
        return text.contains("\u20B1")                   // ₱ (Philippine peso sign — MCParks currency)
            || text.startsWith("You have received")
            || text.startsWith("You've ridden");
    }
}
