package com.chenweikeng.mcparks.monkeycraft;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.monkeycraft_api.v1.MonkeycraftApi;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/**
 * Coordinates PC-side audio with the MonkeyCraft phone companion so the two
 * don't play at once. When the phone attaches, the PC hands off playback;
 * when it detaches, the PC resumes — but only if playback was already on
 * before the handoff (so we don't spontaneously start audio the user never
 * wanted).
 *
 * <p>Also exposes {@link #isPhoneAttached()} as a gate for the
 * auto-connect-on-join path in {@link MCParksExperienceClient} — joining an
 * MCParks server while the phone is already attached must NOT kick off PC
 * audio.
 *
 * <p>State is process-wide: once a phone attaches we remember it, and the
 * resume flag survives across Minecraft-server disconnect/reconnect so the
 * user's "audio was on" preference is preserved if they leave and re-join
 * mid-handoff.
 */
public final class MonkeycraftAudioCoordinator {

    /** True between CONNECTION and DISCONNECTION events on the MonkeyCraft API. */
    private static volatile boolean phoneAttached = false;

    /**
     * Was PC audio connected at the moment the phone attached? If yes, we
     * reconnect PC audio on detach; if no, we leave it off (the user never
     * had it on, no need to turn it on now).
     */
    private static volatile boolean resumeOnDetach = false;

    private MonkeycraftAudioCoordinator() {}

    public static void register() {
        MonkeycraftApi.CONNECTION.register(remoteAddress -> onPhoneAttached());
        MonkeycraftApi.DISCONNECTION.register(MonkeycraftAudioCoordinator::onPhoneDetached);
    }

    /** Gate for auto-connect-on-server-join: skip if phone is currently attached. */
    public static boolean isPhoneAttached() {
        return phoneAttached;
    }

    private static void onPhoneAttached() {
        phoneAttached = true;
        MCParksAudioService audio = MCParksAudioService.getInstance();
        if (audio.isConnected()) {
            resumeOnDetach = true;
            MCParksExperienceClient.LOGGER.info(
                "[MonkeyCraft] client attached; handing off PC audio to client");
            audio.disconnect();
        } else {
            resumeOnDetach = false;
            MCParksExperienceClient.LOGGER.info(
                "[MonkeyCraft] client attached; PC audio was not running, nothing to hand off");
        }
    }

    private static void onPhoneDetached() {
        phoneAttached = false;
        if (!resumeOnDetach) {
            MCParksExperienceClient.LOGGER.info(
                "[MonkeyCraft] client detached; PC audio was off before attach, leaving it off");
            return;
        }
        resumeOnDetach = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getUser() == null) return;
        String username = mc.getUser().getName();
        MCParksExperienceClient.LOGGER.info(
            "[MonkeyCraft] client detached; resuming PC audio as {}", username);
        CompletableFuture.runAsync(() -> MCParksAudioService.getInstance().connect(username));
    }
}
