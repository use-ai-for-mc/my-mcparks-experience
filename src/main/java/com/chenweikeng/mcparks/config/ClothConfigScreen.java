package com.chenweikeng.mcparks.config;

import com.chenweikeng.mcparks.audio.MCParksAudioService;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class ClothConfigScreen {

    public static Object createScreen(net.minecraft.client.gui.screens.Screen parent) {
        ConfigSetting config = ModConfig.currentSetting;

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("config.mcparks.title"))
            .setSavingRunnable(() -> ModConfig.save());

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // --- Audio category ---
        ConfigCategory audio = builder.getOrCreateCategory(
            Component.translatable("config.mcparks.category.audio"));

        audio.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("config.mcparks.autoConnect"),
                config.autoConnect)
            .setDefaultValue(ConfigDefaults.AUTO_CONNECT)
            .setTooltip(Component.translatable("config.mcparks.autoConnect.tooltip"))
            .setSaveConsumer(newValue -> config.autoConnect = newValue)
            .build());

        audio.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("config.mcparks.volume"),
                config.volume, 0, 100)
            .setDefaultValue(ConfigDefaults.VOLUME)
            .setTooltip(Component.translatable("config.mcparks.volume.tooltip"))
            .setSaveConsumer(newValue -> {
                config.volume = newValue;
                MCParksAudioService.getInstance().setUserVolumeFromSlider(newValue);
            })
            .build());

        // --- Gameplay category ---
        ConfigCategory gameplay = builder.getOrCreateCategory(
            Component.translatable("config.mcparks.category.gameplay"));

        gameplay.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("config.mcparks.cursorReleaseOnRide"),
                config.cursorReleaseOnRide)
            .setDefaultValue(ConfigDefaults.CURSOR_RELEASE_ON_RIDE)
            .setTooltip(Component.translatable("config.mcparks.cursorReleaseOnRide.tooltip"))
            .setSaveConsumer(newValue -> config.cursorReleaseOnRide = newValue)
            .build());

        gameplay.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("config.mcparks.hideMountMessageOnRide"),
                config.hideMountMessageOnRide)
            .setDefaultValue(ConfigDefaults.HIDE_MOUNT_MESSAGE_ON_RIDE)
            .setTooltip(Component.translatable("config.mcparks.hideMountMessageOnRide.tooltip"))
            .setSaveConsumer(newValue -> config.hideMountMessageOnRide = newValue)
            .build());

        gameplay.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("config.mcparks.autoFly"),
                config.autoFly)
            .setDefaultValue(ConfigDefaults.AUTO_FLY)
            .setTooltip(Component.translatable("config.mcparks.autoFly.tooltip"))
            .setSaveConsumer(newValue -> config.autoFly = newValue)
            .build());

        gameplay.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("config.mcparks.speedMultiplier"),
                (int) (config.speedMultiplier * 100), 100, 500)
            .setDefaultValue((int) (ConfigDefaults.SPEED_MULTIPLIER * 100))
            .setTooltip(Component.translatable("config.mcparks.speedMultiplier.tooltip"))
            .setSaveConsumer(newValue -> config.speedMultiplier = newValue / 100.0f)
            .setTextGetter(value -> Component.literal(value + "%"))
            .build());

        gameplay.addEntry(entryBuilder
            .startEnumSelector(
                Component.translatable("config.mcparks.fullbright"),
                FullbrightMode.class,
                config.fullbrightMode)
            .setDefaultValue(ConfigDefaults.FULLBRIGHT_MODE)
            .setTooltip(Component.translatable("config.mcparks.fullbright.tooltip"))
            .setEnumNameProvider(value -> Component.translatable(
                "config.mcparks.fullbright." + value.name().toLowerCase(Locale.ROOT)))
            .setSaveConsumer(newValue -> config.fullbrightMode = (FullbrightMode) newValue)
            .build());

        // --- UI Hiding category ---
        ConfigCategory uiHiding = builder.getOrCreateCategory(
            Component.translatable("config.mcparks.category.uiHiding"));

        uiHiding.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("config.mcparks.hideHealthBar"),
                config.hideHealthBar)
            .setDefaultValue(ConfigDefaults.HIDE_HEALTH_BAR)
            .setTooltip(Component.translatable("config.mcparks.hideHealthBar.tooltip"))
            .setSaveConsumer(newValue -> config.hideHealthBar = newValue)
            .build());

        uiHiding.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("config.mcparks.hideExperienceLevel"),
                config.hideExperienceLevel)
            .setDefaultValue(ConfigDefaults.HIDE_EXPERIENCE_LEVEL)
            .setTooltip(Component.translatable("config.mcparks.hideExperienceLevel.tooltip"))
            .setSaveConsumer(newValue -> config.hideExperienceLevel = newValue)
            .build());

        return builder.build();
    }
}
