package com.chenweikeng.mcparks.monkeycraft;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Coordinator for all MonkeyCraft-API-backed features in this mod. Gating is
 * layered intentionally:
 *
 * <ul>
 *   <li>{@link #isAvailable()} — is the MonkeyCraft companion mod present in
 *       this Fabric instance? Process-wide fact; never changes at runtime.</li>
 *   <li>{@link com.chenweikeng.mcparks.ServerState#isTargetServer()} — is the
 *       client currently talking to an MCParks server? Changes every JOIN /
 *       DISCONNECT.</li>
 * </ul>
 *
 * Callers that touch MonkeyCraft must check BOTH before invoking anything.
 * The first-time {@code init()} registers provider + listeners exactly once;
 * subsequent calls are no-ops, so it is safe to call on every JOIN.
 */
public final class MonkeycraftCompat {

    private static final String MONKEYCRAFT_MOD_ID = "monkeycraft";
    private static boolean initialized = false;

    private MonkeycraftCompat() {}

    /**
     * {@code true} iff the MonkeyCraft companion mod is loaded in this Fabric
     * instance. Reflects mod presence, not server identity or pairing state.
     */
    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(MONKEYCRAFT_MOD_ID);
    }

    /**
     * Wire MonkeyCraft provider + listeners once per process. Call from the
     * JOIN handler guarded by a server-identity check: at client startup the
     * target server is not yet known, and the underlying event registrations
     * must only happen a single time.
     *
     * <p>Idempotent: subsequent calls return immediately via the {@code
     * initialized} flag; a missing MonkeyCraft mod makes this call a no-op.
     */
    public static void init() {
        if (initialized) return;
        if (!isAvailable()) return;
        try {
            MonkeycraftProvider.registerOnce();
            McParksAudioLinkRewriter.register();
            MonkeycraftAudioCoordinator.register();
        } catch (Throwable t) {
            MCParksExperienceClient.LOGGER.warn("MonkeyCraft init failed", t);
            return;
        }
        initialized = true;
    }
}
