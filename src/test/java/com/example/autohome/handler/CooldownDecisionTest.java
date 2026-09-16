package com.example.autohome.handler;

import com.example.autohome.handler.CooldownDecision.Action;
import com.example.autohome.handler.CooldownDecision.State;

public final class CooldownDecisionTest {
    private static final int THRESHOLD = 6;
    private static int checks;

    private CooldownDecisionTest() {
    }

    public static void main(String[] args) {
        armedFiresBelowThreshold();
        armedDoesNotFireAtThreshold();
        armedDoesNotFireAboveThreshold();
        coolingIgnoresLowHealth();
        coolingResetsWhenTimerExpires();
        coolingResetsWhenHealthRecovers();
        timerResetDisabledKeepsCooling();
        healthResetDisabledKeepsCooling();
        healthAtThresholdIsNotAReset();
        expiredCooldownNeverFiresInTheSameTick();
        System.out.println("CooldownDecision: " + checks + " checks passed");
    }

    private static Action decide(State state, long nowTick, long endTick, float health, boolean onTimer, boolean onHealth) {
        return CooldownDecision.decide(state, nowTick, endTick, health, THRESHOLD, onTimer, onHealth);
    }

    private static void expect(Action expected, Action actual, String name) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static void armedFiresBelowThreshold() {
        expect(Action.FIRE, decide(State.ARMED, 0L, 0L, 5.0F, true, true), "armedFiresBelowThreshold");
    }

    private static void armedDoesNotFireAtThreshold() {
        expect(Action.NONE, decide(State.ARMED, 0L, 0L, 6.0F, true, true), "armedDoesNotFireAtThreshold");
    }

    private static void armedDoesNotFireAboveThreshold() {
        expect(Action.NONE, decide(State.ARMED, 0L, 0L, 20.0F, true, true), "armedDoesNotFireAboveThreshold");
    }

    private static void coolingIgnoresLowHealth() {
        expect(Action.NONE, decide(State.COOLDOWN, 10L, 100L, 1.0F, true, true), "coolingIgnoresLowHealth");
    }

    private static void coolingResetsWhenTimerExpires() {
        expect(Action.RESET, decide(State.COOLDOWN, 100L, 100L, 1.0F, true, false), "coolingResetsWhenTimerExpires");
    }

    private static void coolingResetsWhenHealthRecovers() {
        expect(Action.RESET, decide(State.COOLDOWN, 10L, 100L, 7.0F, false, true), "coolingResetsWhenHealthRecovers");
    }

    private static void timerResetDisabledKeepsCooling() {
        expect(Action.NONE, decide(State.COOLDOWN, 500L, 100L, 1.0F, false, false), "timerResetDisabledKeepsCooling");
    }

    private static void healthResetDisabledKeepsCooling() {
        expect(Action.NONE, decide(State.COOLDOWN, 10L, 100L, 20.0F, false, false), "healthResetDisabledKeepsCooling");
    }

    private static void healthAtThresholdIsNotAReset() {
        expect(Action.NONE, decide(State.COOLDOWN, 10L, 100L, 6.0F, false, true), "healthAtThresholdIsNotAReset");
    }

    private static void expiredCooldownNeverFiresInTheSameTick() {
        expect(Action.RESET, decide(State.COOLDOWN, 100L, 100L, 1.0F, true, true), "expiredCooldownNeverFiresInTheSameTick");
    }
}
