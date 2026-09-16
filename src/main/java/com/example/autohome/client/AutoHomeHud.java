package com.example.autohome.client;

import com.example.autohome.AutoHomeMod;
import com.example.autohome.config.AutoHomeConfig;
import com.example.autohome.config.ModConfig;
import com.example.autohome.handler.AutoHomeHandler;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class AutoHomeHud implements HudElement {
    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 9;
    private static final int COLOR = 0xFFFF5555;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        AutoHomeConfig config = ModConfig.get();
        if (!config.feedback.hudEnabled) {
            return;
        }

        AutoHomeHandler handler = AutoHomeMod.handler();
        if (handler == null) {
            return;
        }

        int seconds = handler.remainingSeconds(client);
        if (seconds <= 0) {
            return;
        }

        Font font = client.font;
        Component text = Component.translatable("hud.autohome.cooldown", seconds);
        int width = font.width(text);

        int x = switch (config.feedback.hudCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - width - MARGIN;
        };
        int y = switch (config.feedback.hudCorner) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - LINE_HEIGHT - MARGIN;
        };

        graphics.text(font, text, x, y, COLOR);
    }
}
