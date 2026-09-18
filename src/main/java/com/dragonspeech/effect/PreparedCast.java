package com.dragonspeech.effect;

import com.dragonspeech.spell.CostBreakdown;

/**
 * A fully-priced cast, waiting to be paid for and executed. Producing one
 * of these spends nothing and changes no game state - it only answers
 * "what would this cost, and is it even legal." Actually spending
 * stamina/hunger/hearts and calling handler.apply() belongs to the
 * DrainResolver (next up).
 */
public record PreparedCast(
    boolean accepted,
    String rejectionReason,
    EffectHandler handler,
    EffectInvocation invocation,
    CostBreakdown cost
) {
    public static PreparedCast accepted(EffectHandler handler, EffectInvocation invocation, CostBreakdown cost) {
        return new PreparedCast(true, null, handler, invocation, cost);
    }

    public static PreparedCast rejected(String reason) {
        return new PreparedCast(false, reason, null, null, null);
    }
}
