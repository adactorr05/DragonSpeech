package com.dragonspeech.effect;

/**
 * What happened when a handler ran. magnitudeApplied is left generic
 * (handlers decide what it means - fire-affected targets, heal amount,
 * blocks moved) since it's mainly for logging/feedback/admin auditing,
 * not further game logic.
 */
public record EffectResult(boolean success, String message, float magnitudeApplied) {
    public static EffectResult success(float magnitudeApplied, String message) {
        return new EffectResult(true, message, magnitudeApplied);
    }

    public static EffectResult failure(String message) {
        return new EffectResult(false, message, 0f);
    }
}
