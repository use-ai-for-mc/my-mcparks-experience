package com.chenweikeng.mcparks.ride.experience;

import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Immutable snapshot of the player's current state, passed to every
 * {@link RideExperience} hook. Built once per tick and per chat message by the
 * detector/mixin so experiences don't each have to re-query the client.
 */
public final class ExperienceContext {

    public final Minecraft mc;
    public final LocalPlayer player;
    /** Entity the player is riding, or {@code null} if not a passenger. */
    public final Entity vehicle;
    public final boolean isPassenger;
    public final List<NearbyModel> nearbyModels;
    /** Current Minecraft dimension, or {@code null} if no level. */
    public final ResourceLocation dimension;
    /** MCParks park name parsed from "Traveling to X in Y" chat messages (e.g. "Disneyland Resort"); {@code null} if unknown. */
    public final String currentPark;
    /** MCParks ride id from the same message (e.g. "MainStreetStation"); {@code null} if unknown. */
    public final String currentRideId;

    private ExperienceContext(Minecraft mc, LocalPlayer player, Entity vehicle,
                              boolean isPassenger, List<NearbyModel> nearbyModels,
                              ResourceLocation dimension,
                              String currentPark, String currentRideId) {
        this.mc = mc;
        this.player = player;
        this.vehicle = vehicle;
        this.isPassenger = isPassenger;
        this.nearbyModels = nearbyModels;
        this.dimension = dimension;
        this.currentPark = currentPark;
        this.currentRideId = currentRideId;
    }

    /**
     * Build a context from the current client state. Returns {@code null} if
     * the player is not in a world yet.
     */
    public static ExperienceContext current(List<NearbyModel> nearbyModels) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;

        Entity vehicle = player.getVehicle();
        boolean isPassenger = vehicle != null;

        ResourceLocation dim = mc.level != null ? mc.level.dimension().location() : null;
        String park = ParkTracker.getInstance().currentPark();
        String rideId = ParkTracker.getInstance().currentRideId();

        return new ExperienceContext(
            mc, player, vehicle, isPassenger,
            nearbyModels != null ? nearbyModels : Collections.emptyList(),
            dim, park, rideId
        );
    }

    /** Convenience overload when no nearby scan has been done this tick. */
    public static ExperienceContext current() {
        return current(Collections.emptyList());
    }

    /** Does the current ride id contain any of the given substrings (case-insensitive)? */
    public boolean rideIdMatchesAny(String... substrings) {
        if (currentRideId == null) return false;
        String lower = currentRideId.toLowerCase();
        for (String s : substrings) {
            if (lower.contains(s.toLowerCase())) return true;
        }
        return false;
    }

    /** An invisible armor-stand model near the player's vehicle, keyed by item + damage. */
    public record NearbyModel(int entityId, String itemName, int damage, double distance) {
        public boolean matches(String item, int dmg) {
            return itemName.equals(item) && damage == dmg;
        }
    }
}
