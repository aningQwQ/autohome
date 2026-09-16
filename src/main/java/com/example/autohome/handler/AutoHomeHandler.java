package com.example.autohome.handler;

import com.example.autohome.config.AutoHomeConfig;
import com.example.autohome.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutoHomeHandler implements ClientTickEvents.EndTick {
    private static final Logger LOGGER = LoggerFactory.getLogger("autohome");
    private static final long TICKS_PER_SECOND = 20L;

    private static final class Session {
        private CooldownDecision.State state = CooldownDecision.State.ARMED;
        private long cooldownEndTick;
        private ResourceKey<Level> dimension;
    }

    private final Map<UUID, Session> sessions = new HashMap<>();

    public void clearAll() {
        sessions.clear();
    }

    public void triggerManually(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        AutoHomeConfig config = ModConfig.get();
        if (!config.trigger.enabled || !canAct(player)) {
            return;
        }

        fire(client, player, session(player), config);
    }

    public void resetManually(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        AutoHomeConfig config = ModConfig.get();
        if (!config.cooldown.manualResetEnabled) {
            return;
        }

        Session session = sessions.get(player.getUUID());
        if (session == null || session.state != CooldownDecision.State.COOLDOWN) {
            return;
        }

        reset(session, player, config);
    }

    public int remainingSeconds(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return 0;
        }

        Session session = sessions.get(player.getUUID());
        if (session == null || session.state != CooldownDecision.State.COOLDOWN) {
            return 0;
        }

        long remaining = session.cooldownEndTick - client.level.getGameTime();
        if (remaining <= 0L) {
            return 0;
        }

        return (int) ((remaining + TICKS_PER_SECOND - 1L) / TICKS_PER_SECOND);
    }

    @Override
    public void onEndTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        UUID uuid = player.getUUID();
        if (player.isDeadOrDying() || player.isRemoved()) {
            sessions.remove(uuid);
            return;
        }

        AutoHomeConfig config = ModConfig.get();
        if (!config.trigger.enabled) {
            return;
        }

        Session session = session(player);

        ResourceKey<Level> dimension = client.level.dimension();
        if (session.dimension != null && !session.dimension.equals(dimension)) {
            session.state = CooldownDecision.State.ARMED;
            session.cooldownEndTick = 0L;
        }
        session.dimension = dimension;

        CooldownDecision.Action action = CooldownDecision.decide(
                session.state,
                client.level.getGameTime(),
                session.cooldownEndTick,
                player.getHealth(),
                config.trigger.healthThreshold,
                config.cooldown.resetOnTimer,
                config.cooldown.resetOnHealth);

        if (action == CooldownDecision.Action.RESET) {
            reset(session, player, config);
        } else if (action == CooldownDecision.Action.FIRE && canAct(player)) {
            fire(client, player, session, config);
        }
    }

    private Session session(LocalPlayer player) {
        return sessions.computeIfAbsent(player.getUUID(), key -> new Session());
    }

    private static boolean canAct(LocalPlayer player) {
        return !player.isDeadOrDying()
                && !player.isRemoved()
                && !player.isSpectator()
                && !player.isCreative();
    }

    private void fire(Minecraft client, LocalPlayer player, Session session, AutoHomeConfig config) {
        String command = config.action.command == null ? "" : config.action.command.trim();
        if (command.isEmpty() || !send(player, command)) {
            if (config.feedback.chatNotify) {
                player.sendSystemMessage(Component.translatable("chat.autohome.no_command"));
            }
            return;
        }

        session.state = CooldownDecision.State.COOLDOWN;
        session.cooldownEndTick = client.level.getGameTime() + config.cooldown.cooldownSeconds * TICKS_PER_SECOND;

        if (config.feedback.chatNotify) {
            player.sendSystemMessage(Component.translatable(
                    "chat.autohome.triggered", command, config.trigger.healthThreshold));
        }

        if (config.debug.debugLog) {
            LOGGER.info("AutoHome fired for {} (health={}, command={})",
                    player.getName().getString(), player.getHealth(), command);
        }
    }

    private static boolean send(LocalPlayer player, String command) {
        if (!command.startsWith("/")) {
            player.connection.sendChat(command);
            return true;
        }

        String bare = command.substring(1).trim();
        if (bare.isEmpty()) {
            LOGGER.warn("AutoHome command '{}' has no command name, nothing was sent", command);
            return false;
        }

        player.connection.sendCommand(bare);
        return true;
    }

    private void reset(Session session, LocalPlayer player, AutoHomeConfig config) {
        session.state = CooldownDecision.State.ARMED;
        session.cooldownEndTick = 0L;

        if (config.feedback.chatNotify && config.feedback.resetNotify) {
            player.sendSystemMessage(Component.translatable("chat.autohome.reset_done"));
        }

        if (config.debug.debugLog) {
            LOGGER.info("AutoHome cooldown reset for {}", player.getName().getString());
        }
    }
}
