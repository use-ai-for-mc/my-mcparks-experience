package com.chenweikeng.mcparks.ride.experience.rides;

import com.chenweikeng.mcparks.ride.experience.ExperienceContext;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Haunted Mansion &mdash; 999 happy haunts, with room for one more. The
 * Magic Kingdom (WDW) variant; detected via the {@code "Current Ride"}
 * sidebar scoreboard entry or the doom buggy vehicle model.
 *
 * <p>Vehicle: invisible armor stand with {@code iron_axe:95}
 * ({@code vehicles/doombuggy} in the resource pack).
 *
 * <p>Speaker prefixes observed in chat:
 * <ul>
 *   <li>{@code [Ghost Host]} &mdash; pre-show stretching room + ride narration
 *   <li>{@code [Madame Leota]} &mdash; s&eacute;ance room
 *   <li>{@code [Bride]} &mdash; attic scene
 *   <li>{@code [Little Leota]} &mdash; crypt exit farewell
 * </ul>
 *
 * <p>Unprefixed green-italic lines are also ride dialogue / sound-effect
 * descriptions ({@code "Let me out of here!"}, {@code "*Ghost sounds*"},
 * {@code "*Knock on table*"}, etc.). We detect those by checking the
 * Component's style (italic + green color) and ensuring it isn't player chat.
 *
 * <p>Detection is deliberately <em>not</em> gated on {@code isPassenger}: the
 * Ghost Host narration starts in the pre-show stretching room before the
 * guest boards a Doom Buggy. Once the player does board, the HUD ride-time
 * counter kicks in via {@code scanForRideModels}.
 *
 * <p>MCParks runs all parks in {@code minecraft:overworld} on the 1.19
 * backend &mdash; the dimension is useless as a park discriminator.
 * {@link com.chenweikeng.mcparks.ride.experience.ParkTracker ParkTracker}
 * (parsing {@code "Traveling to X in Y"}) is the only reliable filter.
 *
 * <p>If we later want to distinguish between the WDW / Disneyland / DLP / Tokyo
 * variants (different dialogue tracks), add sibling classes with park-specific
 * {@link #park()} values.
 */
public class HauntedMansion implements RideExperience {

    private static final String NAME = "Haunted Mansion";
    private static final String PARK = "Walt Disney World Resort";

    /** Doom Buggy: iron_axe with damage 95. */
    private static final String VEHICLE_ITEM = "iron_axe";
    private static final int VEHICLE_DAMAGE = 95;

    /** Named speaker prefixes, checked in order. */
    private static final String[] SPEAKER_PREFIXES = {
        "[Ghost Host] ",
        "[Madame Leota] ",
        "[Bride] ",
        "[Little Leota] "
    };

    /** Green color values used by MCParks for ride ambient text. */
    private static final int GREEN = 0x55FF55;       // §a
    private static final int DARK_GREEN = 0x00AA00;   // §2

    @Override public String name() { return NAME; }
    @Override public String park() { return PARK; }

    /** Full doom-buggy cycle: 7 min 3 sec (confirmed from log). */
    @Override public int rideTimeSeconds() { return 7 * 60 + 3; }

    @Override
    public boolean isActive(ExperienceContext ctx) {
        if (!PARK.equals(ctx.currentPark)) return false;
        // Primary: ride name from sidebar scoreboard
        if (ctx.rideNameMatchesAny("Haunted Mansion")) return true;
        // Fallback: player is riding a doom buggy (handles mid-session mod load)
        for (ExperienceContext.NearbyModel m : ctx.nearbyModels) {
            if (m.matches(VEHICLE_ITEM, VEHICLE_DAMAGE)) return true;
        }
        return false;
    }

    @Override
    public Optional<String> captureSubtitle(Component message) {
        String text = message.getString();

        // 1. Named speaker prefixes — strip prefix, subtitle the body
        for (String prefix : SPEAKER_PREFIXES) {
            if (text.startsWith(prefix)) {
                String body = text.substring(prefix.length()).trim();
                return body.isEmpty() ? Optional.empty() : Optional.of(body);
            }
        }

        // 2. Green italic unprefixed text — ambient ride dialogue / SFX
        //    e.g. "Let me out of here!", "*Ghost sounds*", "*Knock on table*"
        if (!text.isEmpty() && isGreenItalic(message) && !isPlayerChat(text)) {
            return Optional.of(text);
        }

        return Optional.empty();
    }

    // -- Style helpers --

    /** Check whether the Component (root or any sibling) carries green italic style. */
    private static boolean isGreenItalic(Component message) {
        if (hasGreenItalicStyle(message.getStyle())) return true;
        for (Component sibling : message.getSiblings()) {
            if (hasGreenItalicStyle(sibling.getStyle())) return true;
        }
        return false;
    }

    private static boolean hasGreenItalicStyle(Style style) {
        if (style == null) return false;
        if (!Boolean.TRUE.equals(style.isItalic())) return false;
        if (style.getColor() == null) return false;
        int color = style.getColor().getValue();
        return color == GREEN || color == DARK_GREEN;
    }

    /** Player chat lines on MCParks follow the pattern "[Role] Name › message". */
    private static boolean isPlayerChat(String text) {
        return text.contains(" \u203A ");  // › (single right-pointing angle quotation mark)
    }
}
