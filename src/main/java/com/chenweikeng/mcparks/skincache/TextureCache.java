package com.chenweikeng.mcparks.skincache;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;

/**
 * File-based texture cache with two-tier eviction (LRU + TTL).
 *
 * <p>Layout on disk: .minecraft/skincache/
 *   index.json — maps texture URL -> CacheEntry (hash, timestamp, lastAccessed)
 *   textures/<sha256>.png — the cached skin file
 *
 * <p>Eviction policy:
 * - Hard TTL: any entry whose lastAccessed is older than 30 days is dropped.
 * - Capacity LRU: when the index has more than 10,000 entries, oldest-accessed entries are dropped.
 */
public final class TextureCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final long DEFAULT_TTL_MS = 30L * 24 * 60 * 60 * 1000L; // 30 days
    private static final long MAX_CACHE_FILES = 10_000;
    private static final long ACCESS_UPDATE_GRANULARITY_MS = 5 * 60 * 1000L; // 5 min
    private static final long PERIODIC_INTERVAL_SEC = 60;

    private static Path cacheDir;
    private static Path texturesDir;
    private static Path indexFile;

    private static final ConcurrentHashMap<String, CacheEntry> index = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SAVE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "skincache-save");
                t.setDaemon(true);
                return t;
            });

    private static final AtomicBoolean saveDirty = new AtomicBoolean(false);
    private static final AtomicBoolean accessDirty = new AtomicBoolean(false);
    private static volatile boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        Path gameDir = FabricLoader.getInstance().getGameDir();
        cacheDir = gameDir.resolve("skincache");
        texturesDir = cacheDir.resolve("textures");
        indexFile = cacheDir.resolve("index.json");

        try {
            Files.createDirectories(texturesDir);
            loadIndex();
            evictExpired();
            MCParksExperienceClient.LOGGER.info("[SkinCache] Initialized with {} cached entries", index.size());
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.error("[SkinCache] Failed to initialize cache directory", e);
        }

        SAVE_EXECUTOR.scheduleAtFixedRate(
                TextureCache::periodicMaintenance,
                PERIODIC_INTERVAL_SEC,
                PERIODIC_INTERVAL_SEC,
                TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                saveIndex();
            } catch (Exception ignored) {
            }
        }, "skincache-shutdown"));
    }

    public static Path getCacheDir() {
        return cacheDir;
    }

    /**
     * Look up a cached texture by its URL. Returns the path to the cached PNG if it exists.
     * Updates lastAccessed on hits.
     */
    public static Optional<Path> get(String textureUrl) {
        CacheEntry entry = index.get(textureUrl);
        if (entry == null) return Optional.empty();

        long now = System.currentTimeMillis();
        long lastAccess = effectiveLastAccessed(entry);

        if (now - lastAccess > DEFAULT_TTL_MS) {
            index.remove(textureUrl);
            saveIndexAsync();
            return Optional.empty();
        }

        Path file = texturesDir.resolve(entry.hash + ".png");
        if (!Files.exists(file)) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Index entry exists but file missing: {}", file);
            index.remove(textureUrl);
            saveIndexAsync();
            return Optional.empty();
        }

        bumpAccess(entry, now, lastAccess);
        return Optional.of(file);
    }

    /**
     * Store a downloaded texture in the cache. Validates the texture BEFORE writing.
     */
    public static boolean put(String textureUrl, byte[] data) {
        if (!TextureValidator.isValid(data)) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Rejecting invalid texture for URL: {}", textureUrl);
            return false;
        }

        String hash = sha256(data);
        Path targetFile = texturesDir.resolve(hash + ".png");

        try {
            Files.write(targetFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            long now = System.currentTimeMillis();
            CacheEntry entry = new CacheEntry();
            entry.hash = hash;
            entry.timestamp = now;
            entry.lastAccessed = now;
            entry.url = textureUrl;
            index.put(textureUrl, entry);

            saveIndexAsync();
            MCParksExperienceClient.LOGGER.debug("[SkinCache] Cached texture {} -> {}", textureUrl, hash);
            return true;
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.error("[SkinCache] Failed to write cache file", e);
            return false;
        }
    }

    /**
     * Lightweight presence check — no file I/O. Updates lastAccessed.
     * Used by render-path mixins on the per-frame hot path.
     */
    public static boolean isCached(String textureUrl) {
        CacheEntry entry = index.get(textureUrl);
        if (entry == null) return false;

        long now = System.currentTimeMillis();
        long lastAccess = effectiveLastAccessed(entry);
        if (now - lastAccess > DEFAULT_TTL_MS) return false;

        bumpAccess(entry, now, lastAccess);
        return true;
    }

    public static void evictExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;

        Iterator<Map.Entry<String, CacheEntry>> it = index.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CacheEntry> mapEntry = it.next();
            CacheEntry e = mapEntry.getValue();
            if (now - effectiveLastAccessed(e) > DEFAULT_TTL_MS) {
                Path file = texturesDir.resolve(e.hash + ".png");
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            MCParksExperienceClient.LOGGER.debug("[SkinCache] Evicted {} expired entries", removed);
            saveIndexAsync();
        }
    }

    public static void evictOverflow() {
        if (index.size() <= MAX_CACHE_FILES) return;

        index.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        (a, b) -> Long.compare(effectiveLastAccessed(a), effectiveLastAccessed(b))))
                .limit(index.size() - MAX_CACHE_FILES)
                .forEach(entry -> {
                    Path file = texturesDir.resolve(entry.getValue().hash + ".png");
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                    index.remove(entry.getKey());
                });

        saveIndexAsync();
    }

    private static void periodicMaintenance() {
        try {
            evictExpired();
            evictOverflow();
            if (accessDirty.compareAndSet(true, false) && !saveDirty.get()) {
                saveIndex();
            }
        } catch (Exception e) {
            MCParksExperienceClient.LOGGER.warn("[SkinCache] Periodic maintenance failed", e);
        }
    }

    private static long effectiveLastAccessed(CacheEntry e) {
        return e.lastAccessed > 0 ? e.lastAccessed : e.timestamp;
    }

    private static void bumpAccess(CacheEntry entry, long now, long lastAccess) {
        if (now - lastAccess > ACCESS_UPDATE_GRANULARITY_MS) {
            entry.lastAccessed = now;
            accessDirty.set(true);
        }
    }

    private static synchronized void loadIndex() {
        if (!Files.exists(indexFile)) return;

        try (Reader reader = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<ConcurrentHashMap<String, CacheEntry>>() {}.getType();
            ConcurrentHashMap<String, CacheEntry> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                index.putAll(loaded);
            }
        } catch (Exception e) {
            MCParksExperienceClient.LOGGER.error("[SkinCache] Failed to load index, starting fresh", e);
        }
    }

    private static void saveIndexAsync() {
        if (saveDirty.compareAndSet(false, true)) {
            SAVE_EXECUTOR.execute(() -> {
                saveDirty.set(false);
                accessDirty.set(false);
                saveIndex();
            });
        }
    }

    private static synchronized void saveIndex() {
        try (Writer writer = Files.newBufferedWriter(
                indexFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(index, writer);
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.error("[SkinCache] Failed to save index", e);
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static class CacheEntry {
        public String hash;
        public long timestamp;
        public long lastAccessed;
        public String url;
    }
}
