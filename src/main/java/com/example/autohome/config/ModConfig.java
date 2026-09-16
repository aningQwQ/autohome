package com.example.autohome.config;

import me.shedaniel.autoconfig.AutoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the resolved config instance plus constants that must stay outside the
 * {@code @Config} class.
 *
 * <p>Cloth Config walks {@code configClass.getDeclaredFields()} without any
 * modifier filter, so every field on that class - including static and final
 * ones - is treated as a config option. A {@code static final} field makes the
 * save button fail with an IllegalAccessException, so constants and the logger
 * live here instead.
 */
public final class ModConfig {
    public static final String DEFAULT_COMMAND = "/spawn";
    public static final Logger LOGGER = LoggerFactory.getLogger("autohome");

    private static AutoHomeConfig instance;

    private ModConfig() {
    }

    public static void init() {
        instance = AutoConfig.getConfigHolder(AutoHomeConfig.class).getConfig();
    }

    public static AutoHomeConfig get() {
        if (instance == null) {
            init();
        }
        return instance;
    }
}
