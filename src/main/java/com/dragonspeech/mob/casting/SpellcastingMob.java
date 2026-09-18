package com.dragonspeech.mob.casting;

import net.minecraft.world.entity.LivingEntity;

/**
 * Any entity that can dynamically cast spells from its own known words.
 * Everything else in this package (MobSpellComposer, MobCastExecutor,
 * MobSpellCastGoal, MobEffectExecutor) talks to entities ONLY through this
 * interface, never through a concrete class - that's what lets a
 * PathfinderMob-based mob (SpellcastingMobEntity: Elf, Elder Elf, Human
 * Mage) and a Monster-based mob (ShadeEntity) share the exact same
 * spellcasting pipeline despite not sharing a common non-Mob superclass.
 *
 * mysticalEnergy()/maxMysticalEnergy()/setMysticalEnergy() now delegate
 * to com.dragonspeech.stamina.MobStaminaAccess/MobStaminaScaling instead
 * of a field SpellcasterState used to hold itself - per explicit
 * direction ("any entity that uses magic... their stamina should also
 * deplete"), casting needs to spend from the SAME reserve
 * /dragonspeech debug staminaview displays, not a second, disconnected
 * number nothing else in the game ever sees. This is also what makes
 * "any entity that uses magic" (Elves, Shades, Human Mages, Dragons,
 * and more later) automatically get sensible, differentiated stamina
 * for free the moment it implements this interface - MobStaminaScaling
 * checks `instanceof SpellcastingMob` first and uses this caster's own
 * MobPowerTier numbers, before falling back to its generic per-species
 * table for everything else in the game.
 */
public interface SpellcastingMob {

    /** The backing SpellcasterState - implementations just return a stored field. */
    SpellcasterState spellState();

    /** This caster as a LivingEntity - implementations of an Entity subclass should just `return this;`. */
    LivingEntity asEntity();

    default MobVocabulary vocabulary() {
        return spellState().vocabulary();
    }

    default MobPowerTier powerTier() {
        return spellState().tier();
    }

    default float mysticalEnergy() {
        LivingEntity self = asEntity();
        return com.dragonspeech.stamina.MobStaminaAccess.get(self, self.level().getGameTime());
    }

    default float maxMysticalEnergy() {
        return spellState().tier().maxEnergy();
    }

    default void setMysticalEnergy(float value) {
        LivingEntity self = asEntity();
        long now = self.level().getGameTime();
        float current = mysticalEnergy();
        float clamped = Math.max(0f, Math.min(value, maxMysticalEnergy()));
        if (clamped < current) {
            com.dragonspeech.stamina.MobStaminaAccess.spend(self, current - clamped, now);
        }
        // Increasing energy (restoring/healing it) isn't a path anything
        // currently exercises - MobCastExecutor only ever subtracts a
        // cost - so there's deliberately no "add stamina back" branch
        // here yet. Add one (a MobStaminaAccess.restore(...) method)
        // if/when something needs it.
    }

    default boolean canCastNow() {
        return spellState().cooldown() <= 0 && mysticalEnergy() > 0f
            && !com.dragonspeech.wow.WordOfWordsHaltManager.isCastingSuppressed(asEntity());
    }

    /** Called from each implementation's hurt() override, AFTER any ward reduction - see SpellcasterState.recordDamage/pressureFraction and MobCastStrategy. */
    default void recordDamagePressure(float amount) {
        spellState().recordDamage(amount);
    }

    default void markCastUsed() {
        spellState().resetCooldown(asEntity().getRandom());
    }
}
