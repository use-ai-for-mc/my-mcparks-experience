package com.chenweikeng.mcparks;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;

/**
 * Single source of truth for "is the client currently talking to an MCParks
 * server?". Historically the same check was duplicated across half a dozen
 * mixins and handlers; every feature that gates behaviour on server identity
 * should route through here so a future change (e.g. a new MCParks hostname,
 * or a config-driven allowlist) needs to touch exactly one file.
 */
public final class ServerState {

    private static final String TARGET_HOST_SUBSTRING = "mcparks";

    private ServerState() {}

    /**
     * True when the live client session is connected to an MCParks-family
     * server (determined by substring match on the remote host).
     *
     * <p>Returns {@code false} in single-player, on the title screen, or on
     * any server whose host does not contain {@code "mcparks"}.
     */
    public static boolean isTargetServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        String host = resolveServerHost(mc);
        return host != null && host.toLowerCase().contains(TARGET_HOST_SUBSTRING);
    }

    /**
     * Best-effort retrieval of the remote server's host string.
     *
     * <p>Tries the {@link ServerData} entry (populated when the player clicks
     * a saved server) first, then falls back to the live connection's remote
     * {@link InetSocketAddress} (populated by {@code --server} direct-connect
     * flow where {@code getCurrentServer()} is {@code null}).
     */
    public static String resolveServerHost(Minecraft client) {
        ServerData serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            return serverData.ip;
        }
        ClientPacketListener listener = client.getConnection();
        if (listener != null) {
            Connection conn = listener.getConnection();
            if (conn != null) {
                SocketAddress addr = conn.getRemoteAddress();
                if (addr instanceof InetSocketAddress inet) {
                    return inet.getHostString();
                }
            }
        }
        return null;
    }
}
