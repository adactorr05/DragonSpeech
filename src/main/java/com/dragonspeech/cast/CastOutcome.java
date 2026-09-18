package com.dragonspeech.cast;

import com.dragonspeech.effect.EffectResult;
import com.dragonspeech.stamina.DrainResult;

/**
 * The end-to-end result of a cast attempt, for whatever calls CastExecutor
 * (the casting grid, the guess resolver, chat-casting) to report back to
 * the player.
 */
public record CastOutcome(
    Status status,
    String message,
    DrainResult drain,
    EffectResult effect
) {
    public enum Status { REJECTED, OVERDRAFTED, EXECUTED }

    /** Grammar/cap violation - nothing was spent, nothing happened. */
    public static CastOutcome rejected(String reason) {
        return new CastOutcome(Status.REJECTED, reason, null, null);
    }

    /** The caster paid everything they had and it still wasn't enough - the spell fizzles. */
    public static CastOutcome overdrafted(DrainResult drain) {
        return new CastOutcome(Status.OVERDRAFTED,
            "The word demands more than you have to give, and takes it anyway.", drain, null);
    }

    public static CastOutcome executed(DrainResult drain, EffectResult effect) {
        return new CastOutcome(Status.EXECUTED, effect.message(), drain, effect);
    }
}
