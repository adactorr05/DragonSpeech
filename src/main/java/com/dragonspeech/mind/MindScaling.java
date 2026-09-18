package com.dragonspeech.mind;

/**
 * Optional extra scaling for a mob's MindCombatant pools, layered on top
 * of its SentienceTier baseline. SentienceTier answers "what KIND of
 * mind is this" (a Warden vs. a villager); this answers "how much of
 * that kind, right now, for THIS specific individual" - e.g. a
 * DRAGON-tier hatchling shouldn't hit the duel screen with the same
 * pools as a DRAGON-tier ancient, even though both are the hardest
 * TIER of mind in the game.
 *
 * Implement this on a LivingEntity subclass and MindFortitudeService.
 * buildCombatant() will apply mindPowerMultiplier() to every pool
 * (Focus, Stamina, Willpower, Power, Speed - not Discipline, which
 * stays a flat per-tier value) after the normal tier+variance roll.
 * Entities that don't implement this are unaffected - the multiplier
 * defaults to a no-op 1.0 by simply not being consulted at all.
 */
public interface MindScaling {

    /** Multiplier applied to every pool after the tier baseline and random variance are rolled. 1.0 = no change. */
    float mindPowerMultiplier();
}
