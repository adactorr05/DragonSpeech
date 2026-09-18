package com.dragonspeech.api;

/**
 * NOT A CLASS TO CALL - a discoverable INDEX of the base mod's existing
 * public, read-oriented surfaces an addon (e.g. a Spell Generator block)
 * can already build against today, per the design doc's own ask: "a stable
 * public read-only API for known words/precision/domain data... for the
 * Spell Generator block to use."
 *
 * Deliberately NOT a re-wrapped facade - every surface listed below is
 * already a clean, standalone public class/method with no ServerPlayer-only
 * assumptions baked in, so wrapping it again here would just be a second,
 * driftable copy of the same contract. What was actually missing wasn't the
 * data being readable - it was addon authors having to go find it scattered
 * across the mob/casting and mind packages with no single pointer. This
 * class is that pointer, kept up to date as these surfaces evolve.
 *
 * A SPELL GENERATOR BLOCK, concretely, wants:
 *
 *   1. What words does this specific entity know?
 *      -> {@code SpellcastingMob#vocabulary()} (default method every
 *      spellcasting entity - Shade, Elder Elf, Elf, Human Mage - already
 *      implements) returns its {@code MobVocabulary}, which exposes
 *      {@code knows(ResourceLocation)}, {@code words()} (a read-only Set),
 *      and {@code size()}. This is per-INSTANCE, not per-species - two
 *      Shades can know different words.
 *
 *   2. Generate/compose an actual castable spell from what it knows.
 *      -> {@code MobSpellComposer.compose(SpellcastingMob caster,
 *      SpellIntent intent, LivingEntity target, float energyBudget)}
 *      already does exactly this - picks real words the entity knows to
 *      satisfy a given {@code SpellIntent} (OFFENSIVE/DEFENSIVE/UTILITY/
 *      etc. - see that enum for the full list) within a stamina budget,
 *      and returns a composed spell + its cost, or empty if nothing
 *      castable fits. The addon's "Generation Type/Speed/Domain Bias/
 *      Precision Floor" settings map onto choosing which SpellIntent to
 *      request and what energyBudget to pass, not onto reinventing spell
 *      composition from scratch.
 *
 *   3. Precision / imprecise-cast behavior (for the Preset Spell block's
 *      "Still Casts / Reduced Power / Risk Penalty" setting when an entity
 *      doesn't know every word in a hand-picked sentence).
 *      -> Same cast-execution path real casts already go through
 *      (CastExecutor / PreparedCast) already models precision and
 *      imprecise-cast consequences for ANY caster, not just players - an
 *      addon building a preset sentence should route it through that path
 *      rather than reimplementing precision math independently, so an
 *      addon-cast entity behaves under the exact same rules a player or
 *      the base mod's own mob casting already does.
 *
 *   4. Domain display colors, for rendering Mind Blocks / category colors
 *      that match the rest of the mod (per the design doc's own "Block
 *      category colors are pulled from the EXISTING DomainColors.java").
 *      -> {@code DomainColors} (client source set - com.dragonspeech.
 *      client.grid) - {@code ALL_DOMAINS} and {@code of(String domain)}.
 *      Purely a client-side rendering concern, so an addon's GUI module
 *      can depend on it directly without any base-mod networking involved.
 *
 *   5. An entity's own magic stamina, for SENSE blocks ("Own Magic Stamina
 *      %" / "Own Stamina Regen Rate").
 *      -> {@code MobStaminaAccess.get(LivingEntity, long nowGameTime)} for
 *      current reserve, {@code MobStaminaScaling.maxFor(LivingEntity)} for
 *      max (divide the two for a percent), and {@code MobStaminaScaling.
 *      regenPerSecond(LivingEntity)} for regen rate. All 3 already
 *      per-entity-type aware (see MobStaminaScaling's own doc), not one
 *      flat number for every mob.
 *
 *   6. AI compute budget - if a server owner has set one (config GUI,
 *      Server tab, "AI Compute Budget"), it's already enforced centrally
 *      by MobMindCombatAI itself (a hard cap on actor-decisions per pulse,
 *      server-wide) - an addon's brain doesn't need to check or respect
 *      this manually, it simply won't be called more often than the budget
 *      allows. {@code DragonSpeechConfig.aiComputeBudget()} (0 = unlimited)
 *      is available read-only if an addon wants to display or reason about
 *      the current setting itself.
 *
 * These are today's real, working entry points - if any of them get
 * renamed or reshaped as the base mod evolves, this file should be updated
 * in the same commit, so it stays a trustworthy map rather than stale
 * documentation.
 */
public final class AddonIntegrationPoints {
    private AddonIntegrationPoints() {}
}
