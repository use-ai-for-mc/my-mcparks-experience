package com.chenweikeng.mcparks.firework;

import net.minecraft.client.Minecraft;

/**
 * A specific spot at which the mod recognises the player is positioned to
 * watch fireworks. Each location decides for itself how it fingerprints the
 * world — some use an armor-stand prop (the Walt statue in a park's castle
 * forecourt), some use a sign block entity at a specific coordinate, and
 * future additions may use other mechanisms entirely. That's why the match
 * lives behind {@link #isHereNow} rather than a shared coord/radius check.
 *
 * <p>Contract of {@link #isHereNow}:
 * <ul>
 *   <li>Called from the client thread. Implementations can freely touch
 *       {@code mc.level} / {@code mc.player}.</li>
 *   <li>Should be fast enough to call on every client tick — cheap distance
 *       filter first, then the authoritative entity/block-entity check only
 *       when near.</li>
 *   <li>Returns {@code false} when either game state is missing
 *       ({@code mc.player == null}, etc.) rather than throwing.</li>
 * </ul>
 */
public interface FireworkLocation {

    /** Stable machine identifier, e.g. {@code "magic_kingdom"}. */
    String id();

    /** Human-readable label, e.g. {@code "Magic Kingdom Castle (WDW)"}. */
    String displayName();

    /** True iff the player is currently at this firework location. */
    boolean isHereNow(Minecraft mc);
}
