package com.chenweikeng.mcparks.audio;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamingAudioPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksAudioPlayer");

    private final String url;
    private final boolean looping;
    private final boolean fadeInEnabled;
    private final double seekSeconds;
    private final ScheduledExecutorService scheduler;

    private static final long FADE_IN_DURATION_MS = 2000;

    // Reusable byte buffer to avoid repeated allocations in playback loop
    // Max size: 4608 samples * 2 channels * 2 bytes = 18432 bytes (MP3 max frame)
    private byte[] reusableBuffer = new byte[18432];

    private volatile boolean playing = false;
    private volatile float volume = 1.0f;
    private volatile boolean fadingOut = false;
    private volatile long fadeOutStartMs = 0;
    private volatile long playbackStartMs = 0;
    private volatile boolean volumeChanged = false;
    private int serverVolume;

    private Thread playbackThread;
    private SourceDataLine line;

    public StreamingAudioPlayer(String url, int serverVolume, float userVolumeMultiplier,
                                boolean loop, boolean fadeIn, double seekSeconds,
                                ScheduledExecutorService scheduler) {
        this.url = url;
        this.serverVolume = serverVolume;
        this.looping = loop;
        this.fadeInEnabled = fadeIn;
        this.seekSeconds = seekSeconds;
        this.scheduler = scheduler;
        this.volume = (serverVolume / 100.0f) * userVolumeMultiplier;
    }

    public boolean isLooping() {
        return looping;
    }

    public long getPlaybackStartMs() {
        return playbackStartMs;
    }

    public boolean isActive() {
        return playing;
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    public void start() {
        playing = true;
        playbackStartMs = System.currentTimeMillis();
        LOGGER.debug("Starting playback thread for: {} (volume={}, loop={}, fadeIn={}, seek={})",
                url, volume, looping, fadeInEnabled, seekSeconds);
        playbackThread = new Thread(this::playbackLoop, "MCParks-Audio-" + url.hashCode());
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void playbackLoop() {
        LOGGER.debug("Playback loop started for: {}", url);
        do {
            try {
                if (url.toLowerCase().endsWith(".ogg")) {
                    LOGGER.debug("Using OGG decoder for: {}", url);
                    playOgg();
                } else {
                    LOGGER.debug("Using MP3 decoder for: {}", url);
                    playMp3();
                }
                LOGGER.debug("Playback pass finished for: {} (playing={}, looping={}, fadingOut={})",
                        url, playing, looping, fadingOut);
            } catch (Exception e) {
                if (playing) {
                    LOGGER.error("Audio playback error for {}", url, e);
                }
                break;
            }
        } while (looping && playing && !fadingOut);

        LOGGER.debug("Playback loop exited for: {}", url);
        cleanup();
    }

    private void playMp3() throws Exception {
        InputStream stream = openUrl(url);
        Bitstream bitstream = new Bitstream(stream);
        Decoder decoder = new Decoder();

        AudioFormat format = null;
        double elapsedSeconds = 0;
        boolean seeking = seekSeconds > 0;

        try {
            while (playing) {
                Header frameHeader = bitstream.readFrame();
                if (frameHeader == null) {
                    break;
                }

                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);

                if (format == null) {
                    format = new AudioFormat(
                        decoder.getOutputFrequency(),
                        16,
                        decoder.getOutputChannels(),
                        true,
                        false
                    );
                    LOGGER.debug("MP3 format: {}Hz, {}ch - opening audio line",
                            decoder.getOutputFrequency(), decoder.getOutputChannels());
                    line = AudioSystem.getSourceDataLine(format);
                    line.open(format, 8192);
                    line.start();
                    LOGGER.debug("Audio line opened and started for MP3");
                }

                double frameDuration = frameHeader.ms_per_frame() / 1000.0;
                if (seeking) {
                    elapsedSeconds += frameDuration;
                    if (elapsedSeconds < seekSeconds) {
                        bitstream.closeFrame();
                        continue;
                    }
                    seeking = false;
                }

                short[] pcmData = output.getBuffer();
                int length = output.getBufferLength();
                int byteLen = shortsToBytes(pcmData, length, reusableBuffer);
                applyVolume(reusableBuffer, byteLen, getCurrentVolume());
                line.write(reusableBuffer, 0, byteLen);

                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
            stream.close();
        }
    }

    private void playOgg() throws Exception {
        // OGG Vorbis decoding using JOrbis
        InputStream rawStream = openUrl(url);
        com.jcraft.jogg.SyncState syncState = new com.jcraft.jogg.SyncState();
        com.jcraft.jogg.StreamState streamState = new com.jcraft.jogg.StreamState();
        com.jcraft.jogg.Page page = new com.jcraft.jogg.Page();
        com.jcraft.jogg.Packet packet = new com.jcraft.jogg.Packet();
        com.jcraft.jorbis.Info info = new com.jcraft.jorbis.Info();
        com.jcraft.jorbis.Comment comment = new com.jcraft.jorbis.Comment();
        com.jcraft.jorbis.DspState dspState = new com.jcraft.jorbis.DspState();
        com.jcraft.jorbis.Block block = new com.jcraft.jorbis.Block(dspState);

        syncState.init();
        info.init();
        comment.init();

        boolean headersParsed = false;
        int headersRead = 0;
        AudioFormat format = null;
        double elapsedSeconds = 0;
        boolean seeking = seekSeconds > 0;

        try {
            byte[] readBuffer = new byte[4096];
            outer:
            while (playing) {
                // Read data into sync state
                int offset = syncState.buffer(4096);
                byte[] syncBuffer = syncState.data;
                int bytesRead = rawStream.read(syncBuffer, offset, 4096);
                if (bytesRead <= 0 && !headersParsed) break;
                if (bytesRead > 0) syncState.wrote(bytesRead);
                else syncState.wrote(0);

                while (playing) {
                    int pageResult = syncState.pageout(page);
                    if (pageResult == 0) break; // need more data
                    if (pageResult < 0) continue; // skip corrupt page

                    if (!headersParsed) {
                        if (headersRead == 0) {
                            streamState.init(page.serialno());
                        }
                        streamState.pagein(page);

                        while (headersRead < 3) {
                            int packetResult = streamState.packetout(packet);
                            if (packetResult == 0) break;
                            if (packetResult < 0) continue;
                            if (info.synthesis_headerin(comment, packet) < 0) {
                                LOGGER.error("Error reading OGG Vorbis headers");
                                return;
                            }
                            headersRead++;
                        }

                        if (headersRead >= 3) {
                            headersParsed = true;
                            dspState.synthesis_init(info);
                            block.init(dspState);
                            format = new AudioFormat(info.rate, 16, info.channels, true, false);
                            LOGGER.debug("OGG format: {}Hz, {}ch - opening audio line", info.rate, info.channels);
                            line = AudioSystem.getSourceDataLine(format);
                            line.open(format, 8192);
                            line.start();
                            LOGGER.debug("Audio line opened and started for OGG");
                        }
                        continue;
                    }

                    streamState.pagein(page);
                    while (playing) {
                        int packetResult = streamState.packetout(packet);
                        if (packetResult == 0) break;
                        if (packetResult < 0) continue;

                        if (block.synthesis(packet) == 0) {
                            dspState.synthesis_blockin(block);
                        }

                        float[][][] pcmFloat = new float[1][][];
                        int[] index = new int[info.channels];
                        int samples;
                        while ((samples = dspState.synthesis_pcmout(pcmFloat, index)) > 0) {
                            if (seeking) {
                                elapsedSeconds += (double) samples / info.rate;
                                if (elapsedSeconds < seekSeconds) {
                                    dspState.synthesis_read(samples);
                                    continue;
                                }
                                seeking = false;
                            }

                            int byteLen = samples * info.channels * 2;
                            // Expand reusable buffer if needed (rare, only for large OGG frames)
                            if (reusableBuffer.length < byteLen) {
                                reusableBuffer = new byte[byteLen];
                            }
                            for (int s = 0; s < samples; s++) {
                                for (int ch = 0; ch < info.channels; ch++) {
                                    float val = pcmFloat[0][ch][index[ch] + s] * 32767.0f;
                                    int intVal = Math.max(-32768, Math.min(32767, (int) val));
                                    short sample = (short) intVal;
                                    int byteIndex = (s * info.channels + ch) * 2;
                                    reusableBuffer[byteIndex] = (byte) (sample & 0xFF);
                                    reusableBuffer[byteIndex + 1] = (byte) ((sample >> 8) & 0xFF);
                                }
                            }
                            applyVolume(reusableBuffer, byteLen, getCurrentVolume());
                            line.write(reusableBuffer, 0, byteLen);
                            dspState.synthesis_read(samples);
                        }
                    }

                    if (bytesRead <= 0) break outer;
                }

                if (bytesRead <= 0) break;
            }
        } finally {
            rawStream.close();
            // JOrbis objects (syncState, streamState, dspState, block, info, comment)
            // will be garbage collected - their clear() methods are package-private
        }
    }

    private InputStream openUrl(String audioUrl) throws Exception {
        String resolved = audioUrl;
        if (resolved.startsWith("//")) {
            resolved = "https:" + resolved;
        } else if (!resolved.startsWith("http://") && !resolved.startsWith("https://")) {
            resolved = "https://" + resolved;
        }
        LOGGER.debug("Opening audio URL: {}", resolved);
        InputStream stream = new BufferedInputStream(new URL(resolved).openStream(), 16384);
        LOGGER.debug("Audio URL opened successfully: {}", resolved);
        return stream;
    }

    private int shortsToBytes(short[] shorts, int length, byte[] outBuffer) {
        int byteLen = length * 2;
        for (int i = 0; i < length; i++) {
            outBuffer[i * 2] = (byte) (shorts[i] & 0xFF);
            outBuffer[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return byteLen;
    }

    private void applyVolume(byte[] pcmData, int length, float vol) {
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((pcmData[i + 1] << 8) | (pcmData[i] & 0xFF));
            sample = (short) Math.max(-32768, Math.min(32767, (int) (sample * vol)));
            pcmData[i] = (byte) (sample & 0xFF);
            pcmData[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }

    private float getCurrentVolume() {
        // Flush buffered audio when volume changes so the new level is heard immediately
        if (volumeChanged) {
            volumeChanged = false;
            if (line != null) {
                line.flush();
            }
        }

        float base = this.volume;

        if (fadeInEnabled) {
            long elapsed = System.currentTimeMillis() - playbackStartMs;
            if (elapsed < FADE_IN_DURATION_MS) {
                base *= (elapsed / (float) FADE_IN_DURATION_MS);
            }
        }

        if (fadingOut) {
            long elapsed = System.currentTimeMillis() - fadeOutStartMs;
            if (elapsed >= 3000) {
                playing = false;
                return 0;
            }
            base *= (1.0f - elapsed / 3000.0f);
        }

        return Math.max(0, Math.min(1, base));
    }

    public void setVolume(float effectiveVolume) {
        this.volume = effectiveVolume;
    }

    public void setServerVolume(int serverVol, float userVolumeMultiplier) {
        this.serverVolume = serverVol;
        this.volume = (serverVol / 100.0f) * userVolumeMultiplier;
        this.volumeChanged = true;
    }

    public void updateUserVolume(float userVolumeMultiplier) {
        this.volume = (serverVolume / 100.0f) * userVolumeMultiplier;
        this.volumeChanged = true;
    }

    public int getServerVolume() {
        return serverVolume;
    }

    public void stopWithFade() {
        if (fadingOut) return;
        fadingOut = true;
        fadeOutStartMs = System.currentTimeMillis();
        scheduler.schedule(this::forceStop, 3100, TimeUnit.MILLISECONDS);
    }

    public void forceStop() {
        playing = false;
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
        cleanup();
    }

    private void cleanup() {
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception e) {
                // ignore
            }
            line = null;
        }
    }

    public boolean isPlaying() {
        return playing && !fadingOut;
    }

    public String getUrl() {
        return url;
    }
}
