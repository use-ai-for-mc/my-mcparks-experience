package com.chenweikeng.mcparks.status;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages the native macOS menu-bar helper process and sends short text updates
 * over stdin. Used to show a glanceable ride countdown while the Minecraft
 * window is minimized.
 *
 * <p>Protocol — commands (Java → helper stdin):
 * <pre>
 *   {"cmd":"set","text":"2:45"}
 *   {"cmd":"quit"}
 * </pre>
 * Responses (helper stdout → Java):
 * <pre>
 *   {"type":"ready"}
 *   {"type":"error","message":"..."}
 * </pre>
 */
public class StatusBarBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("StatusBarBridge");
    private static final long READY_TIMEOUT_SECONDS = 5;

    private Process process;
    private BufferedWriter writer;
    private Thread readerThread;
    private volatile boolean running;
    private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    public boolean start() {
        Path helperPath = findHelperBinary();
        if (helperPath == null) {
            LOGGER.warn("Status helper binary not found; menu bar countdown disabled");
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(helperPath.toAbsolutePath().toString());
            pb.redirectErrorStream(false);
            process = pb.start();

            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

            running = true;
            readerThread = new Thread(this::readLoop, "StatusBarBridge-Reader");
            readerThread.setDaemon(true);
            readerThread.start();

            try {
                readyFuture.get(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.error("Status helper did not become ready within {}s", READY_TIMEOUT_SECONDS, e);
                stop();
                return false;
            }

            LOGGER.info("Status helper started (pid={})", process.pid());
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to start status helper process", e);
            return false;
        }
    }

    public void setText(String text) {
        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "set");
        cmd.addProperty("text", text);
        sendCommand(cmd);
    }

    public void stop() {
        running = false;
        try {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("cmd", "quit");
            sendCommand(cmd);
        } catch (Exception ignore) {
            // best effort
        }
        if (process != null) {
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            process = null;
        }
        writer = null;
    }

    public boolean isRunning() {
        return running && process != null && process.isAlive();
    }

    private void sendCommand(JsonObject command) {
        if (writer == null || !isRunning()) {
            return;
        }
        try {
            synchronized (this) {
                writer.write(command.toString());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to send command to status helper: {}", e.getMessage());
        }
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                try {
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                    String type = json.has("type") ? json.get("type").getAsString() : "";
                    switch (type) {
                        case "ready" -> readyFuture.complete(null);
                        case "error" -> LOGGER.warn("Status helper error: {}",
                                json.has("message") ? json.get("message").getAsString() : "");
                        default -> { /* ignored */ }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Unparseable status helper output: {}", line);
                }
            }
        } catch (IOException e) {
            if (running) {
                LOGGER.warn("Status helper stdout read error: {}", e.getMessage());
            }
        }
        running = false;
    }

    private Path findHelperBinary() {
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean isMac = os.contains("mac") || os.contains("darwin");
        if (!isMac) {
            return null;
        }

        String binaryName = "status-helper";
        Path dir = nativeHelperDir();

        Path userPath = dir.resolve(binaryName);
        if (Files.isExecutable(userPath)) {
            return userPath;
        }

        return extractResource("/native/macos/" + binaryName, dir.resolve(binaryName));
    }

    private static Path nativeHelperDir() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("my-mcparks-experience")
                .resolve("native");
    }

    private Path extractResource(String resourcePath, Path targetPath) {
        try (InputStream in = StatusBarBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("Status helper resource not found in JAR: {}", resourcePath);
                return null;
            }
            Files.createDirectories(targetPath.getParent());
            Path tempPath = targetPath.resolveSibling(
                    targetPath.getFileName() + ".tmp" + Thread.currentThread().getId());
            try {
                Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
                tempPath.toFile().setExecutable(true);
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tempPath);
            }
            return targetPath;
        } catch (IOException e) {
            LOGGER.error("Failed to extract status helper {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }
}
