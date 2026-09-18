package com.dragonspeech.spell;

/**
 * Describes what the caster is actually attempting against the real world -
 * resolved by whatever (future) EffectHandler is going to carry the spell
 * out. SpellCostCalculator never invents these numbers itself; they come
 * from the handler looking at the real target (a block's hardness, an
 * entity's mass/health, how far away it is, whether the intended effect is
 * meant to persist, and how many things are being targeted at once).
 *
 * baseTaskMagnitude is the single most important number here - it's the
 * "how hard is this, fundamentally" value that the whole rest of the cost
 * formula scales. A handler moving a dirt block might report 1.0; the same
 * handler asked to move a mountain might report 500.0. Getting these
 * baseline numbers right per-effect-type is the real balancing work of
 * this whole system - the formula in SpellCostCalculator just makes sure
 * precision/scope/permanence/distance modify that baseline consistently.
 */
public record EffortContext(
    float baseTaskMagnitude,
    float distanceFromCaster,
    boolean permanent,
    int targetCount
) {
    public static EffortContext trivial() {
        return new EffortContext(1.0f, 0f, false, 1);
    }
}
