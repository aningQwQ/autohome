package com.example.autohome.client;

import com.example.autohome.config.AutoHomeConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.gui.screens.Screen;

public class AutoHomeModClient implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> (Screen) AutoConfigClient.getConfigScreen(AutoHomeConfig.class, parent).get();
    }
}
