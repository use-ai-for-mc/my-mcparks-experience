package com.chenweikeng.mcparks.audio;

import com.chenweikeng.mcparks.config.ModConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCParksAudioService {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksAudio");
    private static final String WS_URL = "wss://audiossl.mcparks.us/";

    private static MCParksAudioService instance;

    private WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile int userVolume = 100;
    private String lastUsername;

    private final ConcurrentHashMap<String, StreamingAudioPlayer> activeSounds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "MCParks-Audio");
        t.setDaemon(true);
        return t;
    });
    // Reuse HttpClient to avoid repeated thread pool creation
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public static MCParksAudioService getInstance() {
        if (instance == null) {
            instance = new MCParksAudioService();
        }
        return instance;
    }

    private MCParksAudioService() {}

    public void connect(String username) {
        if (connected) {
            LOGGER.info("Already connected to MCParks audio");
            return;
        }

        lastUsername = username;
        LOGGER.info("Connecting to MCParks audio as {}", username);
        notifyUser("Connecting to MCParks audio...");

        try {
            httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URL), new AudioWebSocketListener())
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    ws.sendText("name:" + username, true);
                    connected = true;
                    LOGGER.info("Connected to MCParks audio server");
                    notifyUser("Audio connected!");
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to connect to MCParks audio", ex);
                    notifyUser("Failed to connect to audio server.");
                    return null;
                });
        } catch (Exception e) {
            LOGGER.error("Error creating WebSocket connection", e);
            notifyUser("Error connecting to audio server.");
        }
    }

    public void disconnect() {
        if (!connected && webSocket == null) {
            return;
        }

        LOGGER.info("Disconnecting from MCParks audio");
        connected = false;
        stopAllSounds();

        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "");
            } catch (Exception e) {
                LOGGER.debug("Error closing WebSocket", e);
            }
            webSocket = null;
        }

        notifyUser("Audio disconnected.");
    }

    public void reconnect() {
        String username = lastUsername;
        if (username == null) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getUser() != null) {
                username = client.getUser().getName();
            }
        }
        if (username == null) {
            notifyUser("Cannot reconnect: unknown username.");
            return;
        }

        disconnect();
        final String user = username;
        scheduler.execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            connect(user);
        });
    }

    public void dispose() {
        disconnect();
    }

    // --- Volume ---

    public int getUserVolume() {
        return userVolume;
    }

    public void setUserVolume(int volume) {
        volume = Math.max(0, Math.min(100, volume));
        this.userVolume = volume;
        ModConfig.currentSetting.volume = volume;
        ModConfig.save();
        updateAllSoundsVolume();
    }

    public void setUserVolumeFromSlider(int volume) {
        volume = Math.max(0, Math.min(100, volume));
        this.userVolume = volume;
        ModConfig.currentSetting.volume = volume;
        ModConfig.save();
        updateAllSoundsVolume();
    }

    public void setUserVolumeInternal(int volume) {
        this.userVolume = Math.max(0, Math.min(100, volume));
    }

    private void updateAllSoundsVolume() {
        float userMultiplier = userVolume / 100.0f;
        activeSounds.values().forEach(player -> player.updateUserVolume(userMultiplier));
    }

    public boolean isConnected() {
        return connected;
    }

    // --- Message Handling ---
    //
    // Protocol (from MCParks web client bundle.js):
    //   "stop"                  — stop all sounds (fade out 3s)
    //   "stop-<name>"           — stop sound by name
    //   "loop-<seekMs>-<name>"  — loop audio, seek to seekMs/1000. If seekMs > 1000, fade in from 0.
    //   "show-<seekMs>-<name>"  — one-shot audio (same as loop but no looping)
    //   "<name>"                — play audio at default volume, no loop, no seek
    //
    // All audio URLs are: https://mcparks.us/audio_files/<name>.mp3
    // Default volume is 0.5 (50%). Server volume via Howler global = user_volume/100.

    private static final String AUDIO_BASE_URL = "https://mcparks.us/audio_files/";
    private static final int DEFAULT_SERVER_VOLUME = 50;

    private void handleMessage(String message) {
        LOGGER.debug("Audio message received: {}", message);

        if (message.equals("stop")) {
            stopAllSounds();
            return;
        }

        if (message.startsWith("stop-")) {
            // stop-<name> — name is everything after first '-'
            String name = message.substring(5);
            LOGGER.debug("Stopping sound by name: {}", name);
            stopSound(name);
            return;
        }

        if (message.startsWith("loop-")) {
            // loop-<seekMs>-<name>
            String payload = message.substring(5);
            int dash = payload.indexOf('-');
            if (dash <= 0) {
                LOGGER.warn("Malformed loop message: {}", message);
                return;
            }
            try {
                int seekMs = Integer.parseInt(payload.substring(0, dash));
                String name = payload.substring(dash + 1);
                String url = AUDIO_BASE_URL + name + ".mp3";
                double seekSec = seekMs / 1000.0;
                boolean fadeIn = seekMs > 1000;
                LOGGER.debug("Loop: name={}, url={}, seekSec={}, fadeIn={}", name, url, seekSec, fadeIn);
                playSound(name, url, DEFAULT_SERVER_VOLUME, true, fadeIn, seekSec);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid loop seekMs: {}", message, e);
            }
            return;
        }

        if (message.startsWith("show-")) {
            // show-<seekMs>-<name> — same as loop but loop=false
            String payload = message.substring(5);
            int dash = payload.indexOf('-');
            if (dash <= 0) {
                LOGGER.warn("Malformed show message: {}", message);
                return;
            }
            try {
                int seekMs = Integer.parseInt(payload.substring(0, dash));
                String name = payload.substring(dash + 1);
                String url = AUDIO_BASE_URL + name + ".mp3";
                double seekSec = seekMs / 1000.0;
                boolean fadeIn = seekMs > 1000;
                LOGGER.debug("Show: name={}, url={}, seekSec={}, fadeIn={}", name, url, seekSec, fadeIn);
                playSound(name, url, DEFAULT_SERVER_VOLUME, false, fadeIn, seekSec);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid show seekMs: {}", message, e);
            }
            return;
        }

        // Bare name — play at default volume, no loop, no seek
        String name = message;
        String url = AUDIO_BASE_URL + name + ".mp3";
        LOGGER.debug("Play (bare name): name={}, url={}", name, url);
        playSound(name, url, DEFAULT_SERVER_VOLUME, false, false, 0);
    }

    // --- Sound Management ---

    private void playSound(String name, String url, int serverVolume, boolean loop, boolean fadeIn, double seekSeconds) {
        LOGGER.debug("Playing: name={} url={} vol={} loop={} fadeIn={} seek={}", name, url, serverVolume, loop, fadeIn, seekSeconds);

        // Stop existing sound with same name if any
        StreamingAudioPlayer existing = activeSounds.remove(name);
        if (existing != null) {
            existing.forceStop();
        }

        float userMultiplier = userVolume / 100.0f;
        StreamingAudioPlayer player = new StreamingAudioPlayer(
            url, serverVolume, userMultiplier, loop, fadeIn, seekSeconds, scheduler
        );
        activeSounds.put(name, player);
        player.start();
    }

    public void stopAllSoundsPublic() {
        stopAllSounds();
    }

    private void stopAllSounds() {
        LOGGER.debug("Stopping all sounds ({} active)", activeSounds.size());
        // Take a snapshot of current entries and remove them from the map immediately
        // so new sounds added during fade-out won't be orphaned
        var snapshot = new java.util.ArrayList<>(activeSounds.entrySet());
        for (var entry : snapshot) {
            activeSounds.remove(entry.getKey(), entry.getValue());
            entry.getValue().stopWithFade();
        }
    }

    private void stopSound(String name) {
        StreamingAudioPlayer player = activeSounds.remove(name);
        if (player != null) {
            LOGGER.debug("Stopping sound: {}", name);
            player.stopWithFade();
        } else {
            LOGGER.debug("Stop requested for '{}' but not found. Active: {}", name, activeSounds.keySet());
        }
    }

    private void changeVolume(String name, int newServerVolume) {
        StreamingAudioPlayer player = activeSounds.get(name);
        if (player != null) {
            float userMultiplier = userVolume / 100.0f;
            player.setServerVolume(newServerVolume, userMultiplier);
        } else {
            LOGGER.debug("Volume change for '{}' but not found. Active: {}", name, activeSounds.keySet());
        }
    }

    // --- Chat Notification ---

    public static void notifyUser(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.displayClientMessage(
                        Component.literal("\u00A7e[MCParks] \u00A7f" + message), false
                    );
                }
            });
        }
    }

    // --- WebSocket Listener ---

    private class AudioWebSocketListener implements WebSocket.Listener {
        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            LOGGER.info("WebSocket connection opened");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOGGER.info("WebSocket closed: {} {}", statusCode, reason);
            connected = false;
            stopAllSounds();
            notifyUser("Audio connection closed.");
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOGGER.error("WebSocket error", error);
            connected = false;
        }
    }
}
