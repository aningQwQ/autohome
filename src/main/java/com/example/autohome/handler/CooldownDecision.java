package com.example.autohome.handler;

public final class CooldownDecision {
    public enum State {
        ARMED,
        COOLDOWN
    }

    public enum Action {
        NONE,
        FIRE,
        RESET
    }

    private CooldownDecision() {
    }

    public static Action decide(
            State state,
            long nowTick,
            long cooldownEndTick,
            float health,
            int healthThreshold,
            boolean resetOnTimer,
            boolean resetOnHealth) {
        if (state == State.COOLDOWN) {
            boolean expired = resetOnTimer && nowTick >= cooldownEndTick;
            boolean recovered = resetOnHealth && health > healthThreshold;
            return expired || recovered ? Action.RESET : Action.NONE;
        }

        return health < healthThreshold ? Action.FIRE : Action.NONE;
    }
}
