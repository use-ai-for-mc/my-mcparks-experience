package com.chenweikeng.mcparks.mixin;

import com.chenweikeng.mcparks.ride.RideDetector;
import com.chenweikeng.mcparks.ride.experience.RideExperience;
import com.chenweikeng.mcparks.ride.experience.rides.SpaceshipEarth;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Auto-dismisses the "Create the Future" touch-panel screens that appear
 * mid-ride on Spaceship Earth. These are 1-row chest GUIs
 * ({@link MenuType#GENERIC_9x1}) that ask the player to pick an option,
 * but the choice has no server-side effect on the ride (every pick lands
 * on the same {@code sse/future/leisure/*} personalization). Rather than
 * break the ride's pacing, we cancel screen creation on the client and
 * echo a {@link ServerboundContainerClosePacket} back so the server's
 * container tracking stays consistent.
 *
 * <p>Scoped strictly to:
 * <ul>
 *   <li>{@code GENERIC_9x1} menu type (avoids interfering with shop/trade
 *       GUIs, larger chests, furnaces, etc.)</li>
 *   <li>{@link SpaceshipEarth} being the currently active
 *       {@link RideExperience} (no effect outside that ride)</li>
 * </ul>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerOpenScreenMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksScreenAutoClose");

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void mcparks$autoCloseSSETouchpanel(ClientboundOpenScreenPacket packet,
                                                CallbackInfo ci) {
        if (packet.getType() != MenuType.GENERIC_9x1) return;

        RideDetector detector = RideDetector.getInstance();
        if (detector == null) return;
        RideExperience exp = detector.getCurrentExperience();
        if (!(exp instanceof SpaceshipEarth)) return;

        int containerId = packet.getContainerId();
        LOGGER.info("Auto-closing SSE touchpanel (containerId={}, title='{}')",
                containerId, packet.getTitle().getString());

        ClientPacketListener self = (ClientPacketListener) (Object) this;
        self.send(new ServerboundContainerClosePacket(containerId));

        ci.cancel();
    }
}
