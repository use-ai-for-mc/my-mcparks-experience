package com.chenweikeng.mcparks.ride;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RideDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksRide");
    private static final double SCAN_RADIUS = 5.0;

    private boolean wasPassenger = false;
    private int lastVehicleId = -1;
    private long boardingTimeMs = 0;
    private List<RideModelInfo> currentRideModels = new ArrayList<>();
    private RideRegistry.Ride currentRide = null;

    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;

        boolean isPassenger = player.isPassenger();

        // Detect mounting a new vehicle
        if (isPassenger && (!wasPassenger || vehicleChanged(player))) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                lastVehicleId = vehicle.getId();
                boardingTimeMs = System.currentTimeMillis();
                currentRideModels.clear();
                scanForRideModels(client, vehicle);
            }
        }

        // Detect dismounting
        if (wasPassenger && !isPassenger) {
            onDismount(client);
        }

        if (!isPassenger) {
            lastVehicleId = -1;
        }

        wasPassenger = isPassenger;
    }

    private boolean vehicleChanged(LocalPlayer player) {
        Entity vehicle = player.getVehicle();
        return vehicle != null && vehicle.getId() != lastVehicleId;
    }

    private void scanForRideModels(Minecraft client, Entity vehicle) {
        if (client.level == null) return;

        double x = vehicle.getX();
        double y = vehicle.getY();
        double z = vehicle.getZ();

        AABB searchBox = new AABB(
            x - SCAN_RADIUS, y - SCAN_RADIUS, z - SCAN_RADIUS,
            x + SCAN_RADIUS, y + SCAN_RADIUS, z + SCAN_RADIUS
        );

        // Search for armor stands in the area
        List<ArmorStand> armorStands = client.level.getEntitiesOfClass(
            ArmorStand.class,
            searchBox,
            as -> as.isInvisible() && as.getId() != vehicle.getId()
        );

        for (ArmorStand armorStand : armorStands) {
            ItemStack headItem = armorStand.getItemBySlot(EquipmentSlot.HEAD);
            if (!headItem.isEmpty()) {
                String itemName = headItem.getItem().toString();
                int damage = headItem.getDamageValue();
                double dist = armorStand.distanceTo(vehicle);

                currentRideModels.add(new RideModelInfo(
                    armorStand.getId(),
                    itemName,
                    damage,
                    dist
                ));
            }
        }

        // Find the first ride with known ride time, or just the first known ride
        currentRide = null;
        for (RideModelInfo model : currentRideModels) {
            RideRegistry.Ride ride = RideRegistry.getInstance().getRide(model.itemName, model.damage);
            if (ride != null) {
                if (ride.hasRideTime()) {
                    currentRide = ride;
                    break;
                } else if (currentRide == null) {
                    currentRide = ride;
                }
            }
        }

        // Output boarding message - show model paths and distances for debugging
        if (currentRideModels.isEmpty()) {
            sendMessage(client, "Boarded vehicle (no ride models detected)");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("\u00A7aDetected models:\n");

            for (RideModelInfo model : currentRideModels) {
                String modelPath = RideRegistry.getInstance().getModelPath(model.itemName, model.damage);
                RideRegistry.Ride ride = RideRegistry.getInstance().getRide(model.itemName, model.damage);
                String distStr = String.format("%.1fm", model.distance);
                String itemKey = String.format("%s:%d", model.itemName.replace("minecraft:", ""), model.damage);

                if (ride != null && ride.hasRideTime()) {
                    // Confirmed ride - show in green with ride name
                    sb.append(String.format("\u00A7a  %s \u00A77[%s %s] (%s)\n", modelPath, itemKey, distStr, ride.name));
                } else if (ride != null) {
                    // Known but unconfirmed - show in yellow
                    sb.append(String.format("\u00A7e  %s \u00A77[%s %s]\n", modelPath, itemKey, distStr));
                } else {
                    // Unknown - show in gray
                    sb.append(String.format("\u00A77  %s [%s %s]\n", modelPath, itemKey, distStr));
                }
            }
            sendMessage(client, sb.toString().trim());

            // Log for debugging
            LOGGER.info("Boarded ride with {} models near vehicle {}", currentRideModels.size(), vehicle.getId());
            for (RideModelInfo model : currentRideModels) {
                LOGGER.info("  Model: {} damage={} entityId={}",
                    model.itemName, model.damage, model.entityId);
            }
        }
    }

    private void onDismount(Minecraft client) {
        long durationMs = System.currentTimeMillis() - boardingTimeMs;
        String durationStr = formatDuration(durationMs);

        if (currentRideModels.isEmpty()) {
            sendMessage(client, String.format("Dismounted after %s", durationStr));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("\u00A7cExiting:\n");

            for (RideModelInfo model : currentRideModels) {
                String modelPath = RideRegistry.getInstance().getModelPath(model.itemName, model.damage);
                sb.append(String.format("\u00A7f  %s\n", modelPath));
            }
            sb.append(String.format("\u00A77  Duration: %s", durationStr));
            sendMessage(client, sb.toString().trim());

            LOGGER.info("Dismounted after {} from ride with {} models", durationStr, currentRideModels.size());
        }

        currentRideModels.clear();
        currentRide = null;
        boardingTimeMs = 0;
    }

    // --- Public getters for HUD renderer ---

    public boolean isOnRide() {
        return wasPassenger && currentRide != null;
    }

    public boolean hasRideTime() {
        return currentRide != null && currentRide.hasRideTime();
    }

    public RideRegistry.Ride getCurrentRide() {
        return currentRide;
    }

    public long getBoardingTimeMs() {
        return boardingTimeMs;
    }

    public int getElapsedSeconds() {
        if (boardingTimeMs == 0) return 0;
        return (int) ((System.currentTimeMillis() - boardingTimeMs) / 1000);
    }

    public int getRemainingSeconds() {
        if (currentRide == null || !currentRide.hasRideTime()) return -1;
        int elapsed = getElapsedSeconds();
        int remaining = currentRide.rideTimeSeconds - elapsed;
        return Math.max(0, remaining);
    }

    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0) {
            return String.format("%d min %d sec", minutes, seconds);
        } else {
            return String.format("%d sec", seconds);
        }
    }

    private void sendMessage(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(
                Component.literal("\u00A7b[Ride] \u00A7f" + message),
                false
            );
        }
    }

    public void reset() {
        wasPassenger = false;
        lastVehicleId = -1;
        boardingTimeMs = 0;
        currentRideModels.clear();
        currentRide = null;
    }

    private static class RideModelInfo {
        final int entityId;
        final String itemName;
        final int damage;
        final double distance;

        RideModelInfo(int entityId, String itemName, int damage, double distance) {
            this.entityId = entityId;
            this.itemName = itemName;
            this.damage = damage;
            this.distance = distance;
        }
    }
}
