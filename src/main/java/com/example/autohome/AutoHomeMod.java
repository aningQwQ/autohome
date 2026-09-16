package com.example.autohome;

import com.example.autohome.client.AutoHomeHud;
import com.example.autohome.config.AutoHomeConfig;
import com.example.autohome.config.ModConfig;
import com.example.autohome.handler.AutoHomeHandler;
import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoHomeMod implements ClientModInitializer {
    public static final String MOD_ID = "autohome";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    private static KeyMapping triggerKey;
    private static KeyMapping resetKey;
    private static AutoHomeHandler handler;

    public static AutoHomeHandler handler() {
        return handler;
    }

    @Override
    public void onInitializeClient() {
        AutoConfig.register(AutoHomeConfig.class, GsonConfigSerializer::new);
        ModConfig.init();

        handler = new AutoHomeHandler();
        ClientTickEvents.END_CLIENT_TICK.register(handler);
        ClientTickEvents.END_CLIENT_TICK.register(this::onKeyTick);
        ClientPlayConnectionEvents.DISCONNECT.register((connection, client) -> handler.clearAll());

        triggerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autohome.trigger", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));
        resetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autohome.reset", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "cooldown"), new AutoHomeHud());

        LOGGER.info("AutoHome 2.0 initialized (client-only)");
    }

    private void onKeyTick(Minecraft client) {
        while (triggerKey.consumeClick()) {
            handler.triggerManually(client);
        }
        while (resetKey.consumeClick()) {
            handler.resetManually(client);
        }
    }
}
