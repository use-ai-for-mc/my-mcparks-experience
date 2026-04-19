package com.chenweikeng.mcparks.audiocache;

import com.chenweikeng.mcparks.MCParksExperienceClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Disk-backed MP3 cache for audio streamed from the MCParks server.
 *
 * <p>Layout on disk: .minecraft/mcparksaudio/
 *   index.json           — maps track URL -> CacheEntry (hash, size, timestamp, lastAccessed)
 *   &lt;sha256&gt;.mp3   — the cached audio bytes
 *
 * <p>Eviction policy:
 * - TTL: entries whose lastAccessed is older than 7 days are dropped.
 * - Capacity: when entries exceed 200, or total size exceeds 500 MB,
 *   oldest-accessed entries are dropped until under the cap.
 *
 * <p>Any cache read/write error falls through to the network so playback
 * is never blocked by cache failures.
 */
public final class AudioCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final long DEFAULT_TTL_MS = 7L * 24 * 60 * 60 * 1000L;   // 7 days
    private static final long MAX_CACHE_FILES = 200;
    private static final long MAX_CACHE_BYTES = 500L * 1024 * 1024;         // 500 MB
    private static final long ACCESS_UPDATE_GRANULARITY_MS = 5 * 60 * 1000L;
    private static final long PERIODIC_INTERVAL_SEC = 60;

    private static final int DOWNLOAD_BUFFER = 16384;
    private static final int NETWORK_CONNECT_TIMEOUT_MS = 10_000;
    private static final int NETWORK_READ_TIMEOUT_MS = 30_000;

    private static Path cacheDir;
    private static Path indexFile;

    private static final ConcurrentHashMap<String, CacheEntry> index = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SAVE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "audiocache-save");
                t.setDaemon(true);
                return t;
            });
    /** Background pool for draining the rest of a network stream after the
     *  player has stopped reading (fade-out, force-stop). Keeps the playback
     *  thread's close() non-blocking while still capturing the full mp3 on
     *  behalf of the cache. */
    private static final ExecutorService DRAIN_EXECUTOR =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "audiocache-drain");
                t.setDaemon(true);
                return t;
            });

    private static final AtomicBoolean saveDirty = new AtomicBoolean(false);
    private static final AtomicBoolean accessDirty = new AtomicBoolean(false);
    private static volatile boolean initialized = false;

    private AudioCache() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        Path gameDir = FabricLoader.getInstance().getGameDir();
        cacheDir = gameDir.resolve("mcparksaudio");
        indexFile = cacheDir.resolve("index.json");

        try {
            Files.createDirectories(cacheDir);
            loadIndex();
            reconcileWithDisk();
            evictExpired();
            evictOverflow();
            MCParksExperienceClient.LOGGER.info(
                    "[AudioCache] Initialized with {} cached entries ({} bytes total)",
                    index.size(), totalBytes());
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.error("[AudioCache] Failed to initialize cache directory", e);
        }

        SAVE_EXECUTOR.scheduleAtFixedRate(
                AudioCache::periodicMaintenance,
                PERIODIC_INTERVAL_SEC,
                PERIODIC_INTERVAL_SEC,
                TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                saveIndex();
            } catch (Exception ignored) {
            }
        }, "audiocache-shutdown"));
    }

    /**
     * Main entry point: return an InputStream for the given audio URL.
     *
     * <p>On HIT: returns a stream over the cached bytes immediately.
     *
     * <p>On MISS: opens a network stream and wraps it so every chunk the
     * player consumes is also teed to a temp file. When the stream closes,
     * the temp file is promoted into the cache. If the player stopped
     * reading before EOF (fade-out, force-stop), the remainder is drained
     * in the background so the full file still ends up cached. Any
     * cache-layer failure falls through to a plain network stream so
     * playback is never blocked by caching.
     *
     * @throws IOException if the network fetch itself fails
     */
    public static InputStream openStream(String url) throws IOException {
        // Try cache first. Any exception => treat as miss.
        try {
            Optional<byte[]> cached = readFromCache(url);
            if (cached.isPresent()) {
                MCParksExperienceClient.LOGGER.info("[AudioCache] HIT {}", url);
                return new ByteArrayInputStream(cached.get());
            }
        } catch (Exception e) {
            MCParksExperienceClient.LOGGER.warn("[AudioCache] Cache read failed; falling through to network: {}", url, e);
        }

        MCParksExperienceClient.LOGGER.info("[AudioCache] MISS {}", url);

        HttpURLConnection conn = openConnection(url);
        InputStream network = new BufferedInputStream(conn.getInputStream(), DOWNLOAD_BUFFER);
        long contentLength = conn.getContentLengthLong();

        // If the cache isn't initialized, fall through to a plain network stream.
        if (cacheDir == null) {
            return network;
        }

        // Try to open a temp file for teeing. On failure, fall through to plain network.
        Path tempPath = cacheDir.resolve("dl-" + UUID.randomUUID() + ".tmp");
        OutputStream tempOut;
        try {
            tempOut = new BufferedOutputStream(
                    Files.newOutputStream(tempPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING),
                    DOWNLOAD_BUFFER);
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.warn("[AudioCache] Failed to open temp file, streaming without cache", e);
            return network;
        }

        try {
            return new CachingTeeInputStream(url, network, tempOut, tempPath, contentLength);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 not available — should never happen. Close tee pieces, return plain stream.
            try { tempOut.close(); } catch (IOException ignored) {}
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
            return network;
        }
    }

    /** Open the HTTP connection, configure timeouts, and return it. Caller
     *  uses getInputStream() which implicitly throws on non-2xx responses. */
    private static HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(NETWORK_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(NETWORK_READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    /** Finalize a completed temp download: validate header, rename to
     *  &lt;hash&gt;.mp3, register in the index. Best-effort; logs on failure. */
    static void promoteTempToCache(String url, Path tempPath, String hash, long sizeBytes) {
        try {
            byte[] head = new byte[3];
            try (InputStream in = Files.newInputStream(tempPath)) {
                int r = in.read(head);
                if (r < 3 || !isValidMp3Header(head)) {
                    MCParksExperienceClient.LOGGER.warn(
                            "[AudioCache] Downloaded file failed mp3 validation, not caching: {}", url);
                    Files.deleteIfExists(tempPath);
                    return;
                }
            }

            Path target = cacheDir.resolve(hash + ".mp3");
            try {
                Files.move(tempPath, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, target, StandardCopyOption.REPLACE_EXISTING);
            }

            long now = System.currentTimeMillis();
            CacheEntry entry = new CacheEntry();
            entry.hash = hash;
            entry.sizeBytes = sizeBytes;
            entry.timestamp = now;
            entry.lastAccessed = now;
            entry.url = url;
            index.put(url, entry);
            saveIndexAsync();
            MCParksExperienceClient.LOGGER.debug(
                    "[AudioCache] Cached via streaming {} -> {} ({} bytes)", url, hash, sizeBytes);
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.warn("[AudioCache] Failed to promote temp cache file", e);
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
        }
    }

    /** Discard an incomplete download. */
    static void discardTemp(Path tempPath) {
        try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
    }

    static ExecutorService drainExecutor() {
        return DRAIN_EXECUTOR;
    }

    /** Read the cached bytes for a URL, validating the mp3 header. */
    private static Optional<byte[]> readFromCache(String url) {
        CacheEntry entry = index.get(url);
        if (entry == null) return Optional.empty();

        long now = System.currentTimeMillis();
        long lastAccess = effectiveLastAccessed(entry);
        if (now - lastAccess > DEFAULT_TTL_MS) {
            removeEntry(url, entry);
            return Optional.empty();
        }

        Path file = cacheDir.resolve(entry.hash + ".mp3");
        if (!Files.exists(file)) {
            MCParksExperienceClient.LOGGER.warn("[AudioCache] Index entry exists but file missing: {}", file);
            removeEntry(url, entry);
            return Optional.empty();
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.warn("[AudioCache] Failed reading cached file; invalidating: {}", file, e);
            removeEntry(url, entry);
            return Optional.empty();
        }

        if (!isValidMp3Header(data)) {
            MCParksExperienceClient.LOGGER.warn("[AudioCache] Cached file failed validation; invalidating: {}", file);
            removeEntry(url, entry);
            return Optional.empty();
        }

        bumpAccess(entry, now, lastAccess);
        return Optional.of(data);
    }

    // --- Eviction ---

    public static void evictExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;

        Iterator<Map.Entry<String, CacheEntry>> it = index.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CacheEntry> mapEntry = it.next();
            CacheEntry e = mapEntry.getValue();
            if (now - effectiveLastAccessed(e) > DEFAULT_TTL_MS) {
                deleteFile(e.hash);
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            MCParksExperienceClient.LOGGER.debug("[AudioCache] Evicted {} expired entries", removed);
            saveIndexAsync();
        }
    }

    public static void evictOverflow() {
        long totalBytes = totalBytes();
        if (index.size() <= MAX_CACHE_FILES && totalBytes <= MAX_CACHE_BYTES) return;

        List<Map.Entry<String, CacheEntry>> sorted = new ArrayList<>(index.entrySet());
        sorted.sort((a, b) -> Long.compare(
                effectiveLastAccessed(a.getValue()),
                effectiveLastAccessed(b.getValue())));

        int removed = 0;
        for (Map.Entry<String, CacheEntry> e : sorted) {
            if (index.size() <= MAX_CACHE_FILES && totalBytes <= MAX_CACHE_BYTES) break;
            totalBytes -= e.getValue().sizeBytes;
            deleteFile(e.getValue().hash);
            index.remove(e.getKey());
            removed++;
        }

        if (removed > 0) {
            MCParksExperienceClient.LOGGER.debug("[AudioCache] Evicted {} entries for capacity", removed);
            saveIndexAsync();
        }
    }

    /** Drop index entries whose file is missing; delete orphan files not in the index. */
    private static void reconcileWithDisk() {
        int droppedIndex = 0;
        Iterator<Map.Entry<String, CacheEntry>> it = index.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CacheEntry> e = it.next();
            Path file = cacheDir.resolve(e.getValue().hash + ".mp3");
            if (!Files.exists(file)) {
                it.remove();
                droppedIndex++;
            }
        }

        Set<String> validHashes = new HashSet<>();
        for (CacheEntry e : index.values()) validHashes.add(e.hash);

        int deletedFiles = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(cacheDir, "*.mp3")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                String hash = name.substring(0, name.length() - 4);
                if (!validHashes.contains(hash)) {
                    try {
                        Files.deleteIfExists(p);
                        deletedFiles++;
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.warn("[AudioCache] Failed to scan cache directory", e);
        }

        // Clean up any leftover .tmp files from crashed writes.
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(cacheDir, "*.mp3.tmp")) {
            for (Path p : ds) {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {
        }

        if (droppedIndex > 0 || deletedFiles > 0) {
            MCParksExperienceClient.LOGGER.info(
                    "[AudioCache] Reconcile: dropped {} stale index entries, deleted {} orphan files",
                    droppedIndex, deletedFiles);
            saveIndexAsync();
        }
    }

    private static void periodicMaintenance() {
        try {
            evictExpired();
            evictOverflow();
            if (accessDirty.compareAndSet(true, false) && !saveDirty.get()) {
                saveIndex();
            }
        } catch (Exception e) {
            MCParksExperienceClient.LOGGER.warn("[AudioCache] Periodic maintenance failed", e);
        }
    }

    // --- Helpers ---

    private static void removeEntry(String url, CacheEntry entry) {
        deleteFile(entry.hash);
        index.remove(url, entry);
        saveIndexAsync();
    }

    private static void deleteFile(String hash) {
        try {
            Files.deleteIfExists(cacheDir.resolve(hash + ".mp3"));
        } catch (IOException ignored) {
        }
    }

    private static long totalBytes() {
        long sum = 0;
        for (CacheEntry e : index.values()) sum += Math.max(0, e.sizeBytes);
        return sum;
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

    /** MP3 payloads start with either "ID3" (ID3v2 tag) or an MPEG frame sync 0xFFEx.
     *  Only the first 3 bytes are inspected, so this works as a streaming pre-check too. */
    static boolean isValidMp3Header(byte[] head) {
        if (head == null || head.length < 3) return false;
        if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return true;
        int b0 = head[0] & 0xFF;
        int b1 = head[1] & 0xFF;
        return b0 == 0xFF && (b1 & 0xE0) == 0xE0;
    }

    // --- Index persistence ---

    private static synchronized void loadIndex() {
        if (!Files.exists(indexFile)) return;

        try (Reader reader = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<ConcurrentHashMap<String, CacheEntry>>() {}.getType();
            ConcurrentHashMap<String, CacheEntry> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                index.putAll(loaded);
            }
        } catch (Exception e) {
            MCParksExperienceClient.LOGGER.error("[AudioCache] Failed to load index, starting fresh", e);
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
        if (indexFile == null) return;
        try (Writer writer = Files.newBufferedWriter(
                indexFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(index, writer);
        } catch (IOException e) {
            MCParksExperienceClient.LOGGER.error("[AudioCache] Failed to save index", e);
        }
    }

    static String toHex(byte[] hash) {
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    public static class CacheEntry {
        public String hash;
        public long sizeBytes;
        public long timestamp;
        public long lastAccessed;
        public String url;
    }

    /**
     * InputStream that forwards bytes from the network to the consumer while
     * also teeing them into a temp file and updating a running SHA-256. On
     * close, the temp file is promoted to the cache if the stream was fully
     * drained; otherwise the remainder is drained asynchronously so the cache
     * still captures the whole track. Fade-outs and force-stops therefore
     * don't block the playback thread.
     */
    private static final class CachingTeeInputStream extends InputStream {
        private final String url;
        private final InputStream upstream;
        private final OutputStream tempOut;
        private final Path tempPath;
        private final long contentLength; // -1 if unknown
        private final MessageDigest sha;

        private long bytesWritten = 0;
        private boolean sawEof = false;
        private boolean writeFailed = false;
        private boolean closed = false;

        CachingTeeInputStream(String url, InputStream upstream, OutputStream tempOut,
                              Path tempPath, long contentLength) throws NoSuchAlgorithmException {
            this.url = url;
            this.upstream = upstream;
            this.tempOut = tempOut;
            this.tempPath = tempPath;
            this.contentLength = contentLength;
            this.sha = MessageDigest.getInstance("SHA-256");
        }

        @Override
        public int read() throws IOException {
            int b = upstream.read();
            if (b < 0) {
                sawEof = true;
                return -1;
            }
            if (!writeFailed) {
                try {
                    tempOut.write(b);
                    sha.update((byte) b);
                    bytesWritten++;
                } catch (IOException e) {
                    handleWriteFailure(e);
                }
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = upstream.read(buf, off, len);
            if (n < 0) {
                sawEof = true;
                return -1;
            }
            if (!writeFailed && n > 0) {
                try {
                    tempOut.write(buf, off, n);
                    sha.update(buf, off, n);
                    bytesWritten += n;
                } catch (IOException e) {
                    handleWriteFailure(e);
                }
            }
            return n;
        }

        @Override
        public int available() throws IOException {
            return upstream.available();
        }

        private void handleWriteFailure(IOException e) {
            writeFailed = true;
            MCParksExperienceClient.LOGGER.warn(
                    "[AudioCache] Temp write failed, dropping cache for this play: {}", url, e);
            try { tempOut.close(); } catch (IOException ignored) {}
            discardTemp(tempPath);
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;

            if (writeFailed) {
                // Already cleaned up; just close the network side.
                upstream.close();
                return;
            }

            // Completed if the player read to EOF, OR if the bytes we saw
            // already match the server-advertised length (covers decoders
            // that don't explicitly hit EOF after the last frame).
            boolean complete = sawEof ||
                    (contentLength > 0 && bytesWritten >= contentLength);

            if (complete) {
                // Fast path: close streams and promote synchronously so a
                // subsequent loop iteration sees the cache entry.
                IOException first = null;
                try { upstream.close(); } catch (IOException e) { first = e; }
                try { tempOut.close(); } catch (IOException e) { if (first == null) first = e; }
                promoteTempToCache(url, tempPath, toHex(sha.digest()), bytesWritten);
                if (first != null) throw first;
                return;
            }

            // Slow path: drain the rest of upstream in the background so
            // the player thread can exit immediately.
            drainExecutor().execute(() -> {
                try {
                    byte[] buf = new byte[DOWNLOAD_BUFFER];
                    int n;
                    while ((n = upstream.read(buf)) > 0) {
                        tempOut.write(buf, 0, n);
                        sha.update(buf, 0, n);
                        bytesWritten += n;
                    }
                    sawEof = true;
                } catch (IOException e) {
                    MCParksExperienceClient.LOGGER.debug(
                            "[AudioCache] Background drain failed for {}: {}", url, e.getMessage());
                } finally {
                    try { upstream.close(); } catch (IOException ignored) {}
                    try { tempOut.close(); } catch (IOException ignored) {}
                    if (sawEof || (contentLength > 0 && bytesWritten >= contentLength)) {
                        promoteTempToCache(url, tempPath, toHex(sha.digest()), bytesWritten);
                    } else {
                        discardTemp(tempPath);
                    }
                }
            });
        }
    }
}
