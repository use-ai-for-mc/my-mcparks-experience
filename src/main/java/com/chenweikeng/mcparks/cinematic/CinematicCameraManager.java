package com.chenweikeng.mcparks.cinematic;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top-level orchestrator for the cinematic camera feature: swaps
 * {@code MinecraftClient#cameraEntity}, blanks real-player input, and restores state
 * on disable. Mirrors the shape of Freecam's {@code Freecam} class but stripped to a
 * single-viewpoint, bounded-movement cinematic tool.
 */
public final class CinematicCameraManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksCinematic");
    private static final int DUMMY_ENTITY_ID = -4201;

    private static final CinematicCameraManager INSTANCE = new CinematicCameraManager();

    private @Nullable CinematicCamera camera;
    private @Nullable CameraType rememberedPerspective;

    public static CinematicCameraManager getInstance() {
        return INSTANCE;
    }

    private CinematicCameraManager() {}

    public boolean isActive() {
        return camera != null;
    }

    public @Nullable CinematicCamera getCamera() {
        return camera;
    }

    public void toggle(Viewpoint viewpoint) {
        if (isActive()) {
            disable();
        } else {
            enable(viewpoint);
        }
    }

    public void enable(Viewpoint viewpoint) {
        Minecraft mc = Minecraft.getInstance();
        if (isActive() || mc.level == null || mc.player == null) {
            return;
        }
        mc.smartCull = false;
        mc.gameRenderer.setRenderHand(false);
        rememberedPerspective = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);

        camera = new CinematicCamera(mc, DUMMY_ENTITY_ID, viewpoint);
        camera.spawn();
        mc.setCameraEntity(camera);
        LOGGER.info("Cinematic camera enabled at viewpoint '{}' origin={}", viewpoint.name(), viewpoint.origin());
    }

    public void disable() {
        Minecraft mc = Minecraft.getInstance();
        if (!isActive()) {
            return;
        }
        mc.smartCull = true;
        mc.gameRenderer.setRenderHand(true);
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            mc.player.input = new KeyboardInput(mc.options);
        }
        camera.despawn();
        camera.input = new Input();
        camera = null;
        if (rememberedPerspective != null) {
            mc.options.setCameraType(rememberedPerspective);
            rememberedPerspective = null;
        }
        LOGGER.info("Cinematic camera disabled");
    }

    /** Called every client tick by {@code MCParksExperienceClient}. */
    public void tick(Minecraft mc) {
        if (!isActive()) return;
        // Keep real player's input blank so WASD only moves the dummy camera.
        if (mc.player != null && mc.player.input instanceof KeyboardInput) {
            Input blank = new Input();
            blank.shiftKeyDown = mc.player.input.shiftKeyDown;
            mc.player.input = blank;
        }
        mc.gameRenderer.setRenderHand(false);
    }

    /** Auto-disable on disconnect so we don't leak a dummy entity across worlds. */
    public void onDisconnect() {
        if (isActive()) {
            disable();
        }
    }

    /** For the dev prototype: a viewpoint anchored at the player's current spot. */
    public @Nullable Viewpoint makePlayerAnchoredViewpoint(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Vec3 origin = new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(), mc.player.getZ());
        return Viewpoint.centered(name, origin, mc.player.getYRot(), mc.player.getXRot(), 2.5, 1.5, 2.5);
    }
}
