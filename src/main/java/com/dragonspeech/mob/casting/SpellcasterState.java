package com.dragonspeech.mob.casting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * The mutable spellcasting state a SpellcastingMob carries: which words
 * it knows and when it's next allowed to attempt a cast.
 *
 * Pulled out as a plain field-holder - composed INTO an entity rather
 * than being a base class every spellcasting mob must extend - because
 * Java has no multiple inheritance and this mod's spellcasting mobs
 * don't all share one vanilla base class: the neutral ones (Elf, Elder
 * Elf, Human Mage) are fine extending PathfinderMob (see
 * SpellcastingMobEntity), but Shade needs to extend Monster instead, for
 * vanilla hostile-mob classification (despawn rules, the hostile-mob
 * spawn cap, Enemy-based targeting helpers). Both SpellcastingMobEntity
 * and ShadeEntity just hold one of these and implement SpellcastingMob's
 * delegating default methods - see that interface.
 *
 * NO LONGER HOLDS ENERGY. Per explicit direction ("their stamina didn't
 * go down at all... it should gradually deplete"), this used to have its
 * OWN energy field, completely disconnected from
 * com.dragonspeech.stamina.MobStaminaAccess - the actual, shared,
 * already-displayed-by-/dragonspeech-debug-staminaview reserve every
 * other entity in the game uses. Two parallel pools, only one of which
 * ever moved. SpellcastingMob's energy accessors now read/write
 * MobStaminaAccess directly instead - see that interface - so casting a
 * spell drains the SAME number the debug command shows, and regen is
 * MobStaminaAccess's own lazy elapsed-time calculation, so this class no
 * longer needs its own per-tick regen logic either. tick() now only
 * handles the cast cooldown.
 */
public final class SpellcasterState {

    private final MobVocabulary vocabulary = new MobVocabulary();
    private final MobPowerTier tier;
    private int cooldown;

    /**
     * "Pressure" - a decaying tally of recent damage taken, the basis
     * for MobCastStrategy's "more spells faster if pressured, steady
     * management otherwise" behavior per explicit direction. Rises on
     * every hit (see SpellcastingMob.recordDamagePressure, called from
     * each entity's hurt() override - AFTER any ward reduction, so a
     * well-warded mob correctly feels less pressured by blocked hits),
     * decays ~1.5%/tick the rest of the time (roughly a several-second
     * half-life) so it reflects "am I being hurt RIGHT NOW" rather than
     * total damage ever taken. Deliberately not persisted - this is
     * moment-to-moment combat awareness, not a permanent record.
     */
    private float recentPressure;

    public SpellcasterState(MobPowerTier tier) {
        this.tier = tier;
    }

    public MobVocabulary vocabulary() {
        return vocabulary;
    }

    public MobPowerTier tier() {
        return tier;
    }

    public int cooldown() {
        return cooldown;
    }

    public void resetCooldown(RandomSource random) {
        this.cooldown = tier.castCooldownTicks() + random.nextInt(20);
    }

    public void recordDamage(float amount) {
        recentPressure += amount;
    }

    /** 0 = untouched recently, 1 = under heavy sustained pressure. Scaled against this tier's own max energy, so what counts as "a lot of damage" scales with how tanky/powerful the mob already is. */
    public float pressureFraction() {
        float scale = Math.max(1f, tier.maxEnergy() * 0.25f);
        return Math.min(1f, recentPressure / scale);
    }

    /** Call once per server tick from the owning entity's customServerAiStep() - cast cooldown countdown plus pressure decay. */
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
        if (recentPressure > 0f) {
            recentPressure *= 0.985f;
            if (recentPressure < 0.01f) {
                recentPressure = 0f;
            }
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cooldown", cooldown);
        tag.put("vocabulary", vocabulary.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        this.cooldown = tag.getInt("cooldown");
        if (tag.contains("vocabulary")) {
            vocabulary.load(tag.getCompound("vocabulary"));
        }
    }
}
