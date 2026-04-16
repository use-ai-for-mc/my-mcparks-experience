package com.chenweikeng.mcparks.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuApiImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent ->
            (net.minecraft.client.gui.screens.Screen) ClothConfigScreen.createScreen(parent);
    }
}
