package com.dragonspeech.effect;

import net.minecraft.resources.ResourceLocation;

/**
 * A single primitive magical operation - ignite, push, heal, and so on.
 * This is the ONLY way a spell can ever change game state. Every handler
 * is registered in compiled code (EffectHandlerRegistry), never loaded
 * from a datapack, which is what stops a clever word combination from
 * ever resolving to something like "run command as op" - words select a
 * handler and shape its parameters, they cannot invent new handler
 * behavior.
 */
public interface EffectHandler {

    ResourceLocation id();

    EffectHandlerCaps caps();

    /**
     * "How hard is this, fundamentally" - called BEFORE any resource is
     * spent, purely to build the EffortContext that SpellCostCalculator
     * needs. Must not mutate any game state.
     */
    float estimateBaseMagnitude(EffectInvocation invocation);

    /**
     * Actually carries out the effect. Only ever called AFTER the caster
     * has successfully paid the calculated cost (see the upcoming
     * DrainResolver) - a handler should never need to check "can they
     * afford this," only "is this within my caps."
     *
     * For a channeled handler (isChanneled() = true), this is instead
     * called once per pulse (roughly once per second) for as long as the
     * channel stays open - see ChannelManager.
     */
    EffectResult apply(EffectInvocation invocation);

    /** True for held/sustained effects (e.g. a continuous push) that drain per-pulse via ChannelManager instead of once via CastExecutor. */
    default boolean isChanneled() {
        return false;
    }

    /** Only meaningful when isChanneled() is true - the stamina cost charged each pulse the channel stays open. */
    default float costPerTick(EffectInvocation invocation) {
        return 0f;
    }

    /** True for effects worked on the caster themself (or something they hold) - no look-target needed; the cast pipeline supplies the caster as the target. */
    default boolean selfTargeting() {
        return false;
    }
}
