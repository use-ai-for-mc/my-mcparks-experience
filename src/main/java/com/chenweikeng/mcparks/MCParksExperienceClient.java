package com.chenweikeng.mcparks;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import com.chenweikeng.mcparks.config.ClothConfigScreen;
import com.chenweikeng.mcparks.config.ModConfig;
import com.chenweikeng.mcparks.cursor.CursorManager;
import com.chenweikeng.mcparks.fly.FlyManager;
import com.chenweikeng.mcparks.fullbright.DayTimeHandler;
import com.chenweikeng.mcparks.ride.RideDetector;
import com.chenweikeng.mcparks.ride.RideHudRenderer;
import com.chenweikeng.mcparks.ride.RidePathRecorder;
import com.chenweikeng.mcparks.ride.RideRegistry;
import com.chenweikeng.mcparks.ride.experience.ParkTracker;
import com.chenweikeng.mcparks.skincache.TextureCache;
import com.chenweikeng.mcparks.skincache.TextureRegistrar;
import com.chenweikeng.mcparks.subtitle.SubtitleManager;
import com.chenweikeng.mcparks.subtitle.SubtitleRenderer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCParksExperienceClient implements ClientModInitializer {
    public static final String MOD_ID = "my-mcparks-experience";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final CursorManager cursorManager = new CursorManager();
    private final FlyManager flyManager = new FlyManager();
    private final DayTimeHandler dayTimeHandler = new DayTimeHandler();
    private final RideDetector rideDetector = new RideDetector();
    private final RidePathRecorder ridePathRecorder = new RidePathRecorder();
    private final RideHudRenderer rideHudRenderer = new RideHudRenderer(rideDetector);
    private final SubtitleRenderer subtitleRenderer = new SubtitleRenderer();
    private ResourceKey<Level> lastDimension = null;
    private net.minecraft.client.gui.screens.Screen pendingScreen = null;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        TextureCache.init();
        LOGGER.info("My MCParks Experience client initialized");

        MCParksAudioService.getInstance().setUserVolumeInternal(
            ModConfig.currentSetting.volume
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            onServerJoin(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            onServerDisconnect();
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        HudRenderCallback.EVENT.register((poseStack, tickDelta) -> {
            rideHudRenderer.render(poseStack, tickDelta);
            subtitleRenderer.render(poseStack, tickDelta);
        });

        ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    private void onClientTick(Minecraft client) {
        // Open pending screen (deferred from command to avoid chat screen race)
        if (pendingScreen != null && client.screen == null) {
            client.setScreen(pendingScreen);
            pendingScreen = null;
        }

        cursorManager.tick(client);
        flyManager.tick(client);
        rideDetector.tick(client);
        ridePathRecorder.tick(client);
        dayTimeHandler.tick(client);
        SubtitleManager.tick();

        // Stop all audio on world change
        if (client.level != null) {
            ResourceKey<Level> currentDimension = client.level.dimension();
            if (lastDimension != null && !lastDimension.equals(currentDimension)) {
                LOGGER.info("World change detected ({} -> {}), stopping all audio",
                        lastDimension.location(), currentDimension.location());
                MCParksAudioService.getInstance().stopAllSoundsPublic();
            }
            lastDimension = currentDimension;
        }
    }

    private void onServerJoin(Minecraft client) {
        String serverIp = resolveServerHost(client);
        if (serverIp == null) return;

        if (serverIp.toLowerCase().contains("mcparks")) {
            MacosDockIconHandler.apply();

            if (ModConfig.currentSetting.autoConnect) {
                LOGGER.info("Joined MCParks server: {}, auto-connecting audio", serverIp);
                String username = client.getUser().getName();
                CompletableFuture.runAsync(() -> MCParksAudioService.getInstance().connect(username));
            }
        }
    }

    private static String resolveServerHost(Minecraft client) {
        // Server list path
        var serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            return serverData.ip;
        }
        // Direct connect / --server launch arg
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

    private void onServerDisconnect() {
        MacosDockIconHandler.reset();
        MCParksAudioService.getInstance().dispose();
        cursorManager.reset();
        flyManager.reset();
        rideDetector.reset();
        ridePathRecorder.reset();
        SubtitleManager.clear();
        TextureRegistrar.clear();
        ParkTracker.getInstance().reset();
        lastDimension = null;
    }

    private void registerCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            CommandBuildContext registryAccess) {

        dispatcher.register(
            ClientCommandManager.literal("audioconnect")
                .executes(context -> {
                    MCParksAudioService service = MCParksAudioService.getInstance();
                    if (service.isConnected()) {
                        MCParksAudioService.notifyUser("Already connected to MCParks audio.");
                    } else {
                        String username = Minecraft.getInstance().getUser().getName();
                        CompletableFuture.runAsync(() -> service.connect(username));
                    }
                    return 1;
                })
        );

        dispatcher.register(
            ClientCommandManager.literal("audiodisconnect")
                .executes(context -> {
                    MCParksAudioService.getInstance().disconnect();
                    return 1;
                })
        );

        dispatcher.register(
            ClientCommandManager.literal("audioreconnect")
                .executes(context -> {
                    CompletableFuture.runAsync(
                        () -> MCParksAudioService.getInstance().reconnect()
                    );
                    return 1;
                })
        );

        dispatcher.register(
            ClientCommandManager.literal("audiolist")
                .executes(context -> {
                    printAudioList();
                    return 1;
                })
        );

        dispatcher.register(
            ClientCommandManager.literal("audiostop")
                .then(
                    ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            boolean stopped = MCParksAudioService.getInstance().stopSoundByName(name);
                            if (stopped) {
                                MCParksAudioService.notifyUser("Stopped audio: " + name);
                            } else {
                                MCParksAudioService.notifyUser(
                                    "No active audio named '" + name + "'. Use /audiolist to see what is playing."
                                );
                            }
                            return 1;
                        })
                )
        );

        dispatcher.register(
            ClientCommandManager.literal("mymcparks")
                .executes(context -> {
                    // Defer screen opening to next tick (after chat screen closes)
                    pendingScreen = (net.minecraft.client.gui.screens.Screen) ClothConfigScreen.createScreen(null);
                    return 1;
                })
        );

        dispatcher.register(
            ClientCommandManager.literal("volume")
                .executes(context -> {
                    int vol = MCParksAudioService.getInstance().getUserVolume();
                    MCParksAudioService.notifyUser("MCParks audio volume: " + vol + "%");
                    return 1;
                })
                .then(
                    ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 100))
                        .executes(context -> {
                            int level = IntegerArgumentType.getInteger(context, "level");
                            MCParksAudioService.getInstance().setUserVolume(level);
                            MCParksAudioService.notifyUser(
                                "MCParks audio volume set to " + level + "%"
                            );
                            return 1;
                        })
                )
        );

        // Debug command to show nearby invisible armor stands with head items
        dispatcher.register(
            ClientCommandManager.literal("nearby")
                .executes(context -> {
                    scanNearbyModels(5.0);
                    return 1;
                })
                .then(
                    ClientCommandManager.argument("radius", DoubleArgumentType.doubleArg(1.0, 20.0))
                        .executes(context -> {
                            double radius = DoubleArgumentType.getDouble(context, "radius");
                            scanNearbyModels(radius);
                            return 1;
                        })
                )
        );
    }

    private void scanNearbyModels(double radius) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        AABB searchBox = new AABB(
            x - radius, y - radius, z - radius,
            x + radius, y + radius, z + radius
        );

        // Find all invisible armor stands with head items
        List<ArmorStand> armorStands = client.level.getEntitiesOfClass(
            ArmorStand.class,
            searchBox,
            ArmorStand::isInvisible
        );

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\u00A7b[Nearby] Scanning %.1fm radius:\n", radius));

        if (armorStands.isEmpty()) {
            sb.append("\u00A77  No invisible armor stands found");
        } else {
            // Sort by distance
            armorStands.sort(Comparator.comparingDouble(as -> as.distanceTo(player)));

            int count = 0;
            for (ArmorStand as : armorStands) {
                ItemStack headItem = as.getItemBySlot(EquipmentSlot.HEAD);
                if (headItem.isEmpty()) continue;

                String itemName = headItem.getItem().toString();
                int damage = headItem.getDamageValue();
                double dist = as.distanceTo(player);
                String modelPath = RideRegistry.getInstance().getModelPath(itemName, damage);
                RideRegistry.Ride ride = RideRegistry.getInstance().getRide(itemName, damage);

                String itemKey = String.format("%s:%d", itemName.replace("minecraft:", ""), damage);
                // Calculate damage fraction like Minecraft does (for debugging)
                float damageFrac = (float) damage / 250.0f;  // iron_axe max durability

                if (ride != null && ride.hasRideTime()) {
                    // Confirmed ride - green
                    sb.append(String.format("\u00A7a  %s \u00A77[%s frac=%.4f %.1fm] (%s)\n", modelPath, itemKey, damageFrac, dist, ride.name));
                } else if (ride != null) {
                    // Known but unconfirmed - yellow
                    sb.append(String.format("\u00A7e  %s \u00A77[%s frac=%.4f %.1fm]\n", modelPath, itemKey, damageFrac, dist));
                } else {
                    // Unknown - gray
                    sb.append(String.format("\u00A77  %s [%s frac=%.4f %.1fm]\n", modelPath, itemKey, damageFrac, dist));
                }
                count++;
            }

            if (count == 0) {
                sb.append("\u00A77  No armor stands with head items found");
            } else {
                sb.append(String.format("\u00A77  Total: %d models", count));
            }
        }

        player.displayClientMessage(Component.literal(sb.toString().trim()), false);
    }

    // --- /audiolist helper ---

    private void printAudioList() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        MCParksAudioService audio = MCParksAudioService.getInstance();
        List<MCParksAudioService.ActiveTrack> tracks = audio.snapshotActive();

        String connStatus = audio.isConnected() ? "\u00A7aconnected" : "\u00A7cdisconnected";
        String header = "\u00A7e[MCParks] \u00A7fAudio " + connStatus
                + "\u00A7f, " + tracks.size() + " active track"
                + (tracks.size() == 1 ? "" : "s") + ":";
        player.displayClientMessage(Component.literal(header), false);

        if (tracks.isEmpty()) {
            player.displayClientMessage(
                Component.literal("\u00A77  (nothing playing)"), false);
            return;
        }

        long now = System.currentTimeMillis();
        for (MCParksAudioService.ActiveTrack t : tracks) {
            String kindBadge;
            if (!t.active()) {
                kindBadge = "\u00A78[STOPPED]";
            } else if (t.fadingOut()) {
                kindBadge = "\u00A76[FADING]";
            } else if (t.looping()) {
                kindBadge = "\u00A7b[LOOP]";
            } else {
                kindBadge = "\u00A7d[ONESHOT]";
            }

            player.displayClientMessage(Component.literal(
                "\u00A77 \u2022 \u00A7f" + t.name() + " " + kindBadge), false);

            String startedAgo = humanDuration(now - t.startedAtMs());
            String triggeredAgo = humanDuration(now - t.lastTriggerAtMs());
            String line2 = String.format(
                "\u00A78    started %s ago \u00B7 triggered %d\u00D7 (last: %s ago) \u00B7 vol %d%%",
                startedAgo, t.triggerCount(), triggeredAgo, t.serverVolume());
            player.displayClientMessage(Component.literal(line2), false);

            String diagnosis = diagnoseTrack(t, now);
            if (diagnosis != null) {
                player.displayClientMessage(Component.literal("\u00A78    " + diagnosis), false);
            }

            player.displayClientMessage(Component.literal(
                "\u00A78    msg: \u00A77" + t.lastServerMessage()), false);
        }

        player.displayClientMessage(Component.literal(
            "\u00A77(use \u00A7f/audiostop <name>\u00A77 to stop a specific track)"), false);
    }

    private static String diagnoseTrack(MCParksAudioService.ActiveTrack t, long now) {
        if (!t.active()) {
            return "\u00A78\u2192 finished playing; stale entry, safe to /audiostop";
        }
        if (t.fadingOut()) {
            return "\u00A78\u2192 fading out";
        }
        if (t.looping() && t.triggerCount() == 1) {
            return "\u00A78\u2192 server sent one loop command; will play until server says stop";
        }
        if (t.looping() && t.triggerCount() > 1) {
            return "\u00A76\u2192 server re-triggered " + t.triggerCount() + "x; possible server-side duplicate";
        }
        if (!t.looping() && (now - t.startedAtMs()) > 30_000) {
            return "\u00A76\u2192 one-shot still running after 30s+; long audio or stuck";
        }
        return null;
    }

    private static String humanDuration(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        if (s < 60) return s + "s";
        long m = s / 60;
        long rs = s % 60;
        if (m < 60) return m + "m " + rs + "s";
        long h = m / 60;
        long rm = m % 60;
        return h + "h " + rm + "m";
    }
}
