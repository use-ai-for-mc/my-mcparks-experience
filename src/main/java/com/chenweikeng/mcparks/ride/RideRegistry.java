package com.chenweikeng.mcparks.ride;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RideRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksRide");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static RideRegistry instance;
    private final Path configPath;

    // Primary data: list of rides
    private RideData rideData = new RideData();

    // Lookup index: "item:damage" -> Ride (built from rideData)
    private Map<String, Ride> vehicleLookup = new HashMap<>();

    public static RideRegistry getInstance() {
        if (instance == null) {
            instance = new RideRegistry();
        }
        return instance;
    }

    private RideRegistry() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("mcparks-rides.json");
        load();
    }

    /**
     * Look up a ride by vehicle item name and damage value.
     * @return The ride, or null if not found
     */
    public Ride getRide(String itemName, int damage) {
        String key = makeKey(itemName, damage);
        return vehicleLookup.get(key);
    }

    /**
     * Get display name for a vehicle, or a fallback if unknown.
     */
    public String getDisplayName(String itemName, int damage) {
        Ride ride = getRide(itemName, damage);
        if (ride != null) {
            return ride.name;
        }
        return String.format("Unknown (%s:%d)", itemName, damage);
    }

    /**
     * Get the model path for a vehicle from the resource pack.
     * Returns the modelPath if found, or a fallback string.
     */
    public String getModelPath(String itemName, int damage) {
        String key = makeKey(itemName, damage);
        Ride ride = vehicleLookup.get(key);
        if (ride != null) {
            for (Vehicle v : ride.vehicles) {
                String vKey = makeKey(v.item, v.damage);
                if (vKey.equals(key)) {
                    return v.modelPath != null ? v.modelPath : key;
                }
            }
        }
        return String.format("%s:%d", itemName.replace("minecraft:", ""), damage);
    }

    /**
     * Add a new ride to the registry.
     */
    public void addRide(Ride ride) {
        rideData.rides.add(ride);
        indexRide(ride);
        save();
        LOGGER.info("Added ride: {} with {} vehicles", ride.name, ride.vehicles.size());
    }

    /**
     * Add a vehicle to an existing ride, or create the ride if it doesn't exist.
     */
    public void addVehicleToRide(String rideName, String park, String itemName, int damage) {
        // Find existing ride
        Ride existingRide = null;
        for (Ride ride : rideData.rides) {
            if (ride.name.equals(rideName)) {
                existingRide = ride;
                break;
            }
        }

        Vehicle vehicle = new Vehicle(itemName, damage);

        if (existingRide != null) {
            existingRide.vehicles.add(vehicle);
            indexVehicle(vehicle, existingRide);
        } else {
            Ride newRide = new Ride();
            newRide.name = rideName;
            newRide.park = park;
            newRide.vehicles.add(vehicle);
            rideData.rides.add(newRide);
            indexRide(newRide);
        }

        save();
        LOGGER.info("Added vehicle {}:{} to ride {}", itemName, damage, rideName);
    }

    public List<Ride> getAllRides() {
        return new ArrayList<>(rideData.rides);
    }

    private String makeKey(String itemName, int damage) {
        String normalized = itemName.replace("minecraft:", "");
        return normalized + ":" + damage;
    }

    private void buildLookupIndex() {
        vehicleLookup.clear();
        for (Ride ride : rideData.rides) {
            indexRide(ride);
        }
    }

    private void indexRide(Ride ride) {
        for (Vehicle vehicle : ride.vehicles) {
            indexVehicle(vehicle, ride);
        }
    }

    private void indexVehicle(Vehicle vehicle, Ride ride) {
        String key = makeKey(vehicle.item, vehicle.damage);
        vehicleLookup.put(key, ride);
    }

    public void load() {
        if (!Files.exists(configPath)) {
            createDefaults();
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Type type = new TypeToken<RideData>() {}.getType();
            RideData loaded = GSON.fromJson(reader, type);
            if (loaded != null && loaded.rides != null) {
                rideData = loaded;
                buildLookupIndex();
                LOGGER.info("Loaded {} rides with {} total vehicle mappings from {}",
                        rideData.rides.size(), vehicleLookup.size(), configPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load ride registry", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(rideData, writer);
            }
            LOGGER.info("Saved {} rides to {}", rideData.rides.size(), configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save ride registry", e);
        }
    }

    private void createDefaults() {
        rideData = new RideData();
        LOGGER.info("Created default ride registry");
    }

    // --- Data Classes ---

    public static class RideData {
        public List<Ride> rides = new ArrayList<>();
    }

    public static class Ride {
        public String name;
        public String baseModel;  // Base model path from resource pack (e.g., "vehicles/slinky")
        public String park;
        public int rideTimeSeconds;
        public List<Vehicle> vehicles = new ArrayList<>();

        public Ride() {}

        public Ride(String name, String park, int rideTimeSeconds) {
            this.name = name;
            this.park = park;
            this.rideTimeSeconds = rideTimeSeconds;
        }

        public boolean hasRideTime() {
            return rideTimeSeconds > 0;
        }

        public String formatRideTime() {
            if (rideTimeSeconds <= 0) return null;
            int minutes = rideTimeSeconds / 60;
            int seconds = rideTimeSeconds % 60;
            if (minutes > 0) {
                return String.format("%d min %d sec", minutes, seconds);
            }
            return String.format("%d sec", seconds);
        }
    }

    public static class Vehicle {
        public String item;
        public int damage;
        public String modelPath;  // Model path from resource pack (e.g., "vehicles/slinky_front")

        public Vehicle() {}

        public Vehicle(String item, int damage) {
            this.item = item;
            this.damage = damage;
        }

        public Vehicle(String item, int damage, String modelPath) {
            this.item = item;
            this.damage = damage;
            this.modelPath = modelPath;
        }
    }
}
