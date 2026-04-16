package com.chenweikeng.mcparks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCParksConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config/my-mcparks-experience.json");

    public static ConfigSetting currentSetting = new ConfigSetting();

    public static void load() {
        File configFile = CONFIG_PATH.toFile();
        if (!configFile.exists()) {
            currentSetting = new ConfigSetting();
            save();
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            currentSetting = GSON.fromJson(reader, ConfigSetting.class);
            if (currentSetting == null) {
                currentSetting = new ConfigSetting();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
            currentSetting = new ConfigSetting();
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(currentSetting, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
