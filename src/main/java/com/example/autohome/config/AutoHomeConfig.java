package com.example.autohome.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;

/**
 * Config model for AutoHome.
 *
 * <p>Do not add static fields to this class or to the nested option classes.
 * Cloth Config collects options with {@code getDeclaredFields()} and no
 * modifier filter, so a static field would be rendered as an option and would
 * break saving, because writing a static final field through reflection throws
 * IllegalAccessException. Shared constants live in {@link ModConfig}.
 */
@Config(name = "autohome")
public class AutoHomeConfig implements ConfigData {
    public enum HudCorner implements SelectionListEntry.Translatable {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        @Override
        public String getKey() {
            return "text.autoconfig.autohome.hudCorner." + name();
        }
    }

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public Trigger trigger = new Trigger();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public Action action = new Action();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public Cooldown cooldown = new Cooldown();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public Feedback feedback = new Feedback();

    @ConfigEntry.Gui.CollapsibleObject
    public Debug debug = new Debug();

    public static class Trigger {
        @ConfigEntry.Gui.Tooltip
        public boolean enabled = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1L, max = 20L)
        public int healthThreshold = 6;
    }

    public static class Action {
        @ConfigEntry.Gui.Tooltip
        public String command = ModConfig.DEFAULT_COMMAND;
    }

    public static class Cooldown {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1L, max = 600L)
        public int cooldownSeconds = 10;

        @ConfigEntry.Gui.Tooltip
        public boolean resetOnTimer = true;

        @ConfigEntry.Gui.Tooltip
        public boolean resetOnHealth = true;

        @ConfigEntry.Gui.Tooltip
        public boolean manualResetEnabled = true;
    }

    public static class Feedback {
        @ConfigEntry.Gui.Tooltip
        public boolean chatNotify = true;

        @ConfigEntry.Gui.Tooltip
        public boolean resetNotify = true;

        @ConfigEntry.Gui.Tooltip
        public boolean hudEnabled = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public HudCorner hudCorner = HudCorner.TOP_RIGHT;
    }

    public static class Debug {
        @ConfigEntry.Gui.Tooltip
        public boolean debugLog = false;
    }

    @Override
    public void validatePostLoad() {
        if (action == null) {
            action = new Action();
        }
        if (action.command == null || action.command.trim().isEmpty()) {
            ModConfig.LOGGER.warn("AutoHome command was blank, falling back to {}", ModConfig.DEFAULT_COMMAND);
            action.command = ModConfig.DEFAULT_COMMAND;
        }

        if (cooldown == null) {
            cooldown = new Cooldown();
        }
        if (!cooldown.resetOnTimer && !cooldown.resetOnHealth && !cooldown.manualResetEnabled) {
            ModConfig.LOGGER.warn("AutoHome has no enabled cooldown reset path, forcing the timer reset on");
            cooldown.resetOnTimer = true;
        }

        if (trigger == null) {
            trigger = new Trigger();
        }
        if (feedback == null) {
            feedback = new Feedback();
        } else if (feedback.hudCorner == null) {
            feedback.hudCorner = HudCorner.TOP_RIGHT;
        }
        if (debug == null) {
            debug = new Debug();
        }
    }
}
