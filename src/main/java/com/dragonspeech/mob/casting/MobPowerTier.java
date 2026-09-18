package com.dragonspeech.mob.casting;

import java.util.Locale;

/**
 * How capable a spellcasting mob is, independent of species. This is what
 * actually controls how big a mob's magic reserve is and how often it can
 * cast - the concrete entity classes (ElfEntity, ElderElfEntity,
 * HumanMageEntity, ShadeEntity) each pick a tier at construction time and
 * everything else follows from that one choice.
 *
 * Deliberately does NOT hand out a fixed spell list per tier. See
 * MobSpellComposer's class comment for why "knows more words + has a bigger
 * energy pool" is enough on its own to make a higher tier cast longer, more
 * precise sentences - which the real SpellCostCalculator formula then makes
 * proportionally CHEAPER per-word than a low-tier mob's crude one-word cast,
 * exactly like it does for a player. A tier is a budget, not a spellbook.
 */
public enum MobPowerTier {
    /** Human Mage territory - "not as powerful as elves" per the design brief. */
    APPRENTICE(40f, 0.6f, 40),
    /** Ordinary Elves. */
    ADEPT(90f, 1.2f, 30),
    /** Elder Elves. */
    ELDER(180f, 2.2f, 20),
    /** Shades - "extremely powerful... a vast amount of magic," and per explicit follow-up direction should also regenerate stamina noticeably faster than the other three ("as part of their buff... hard to defeat") - 6.4/s here vs. the next-fastest tier's 2.2/s. */
    CATASTROPHIC(320f, 6.4f, 12);

    private final float maxEnergy;
    private final float energyRegenPerSecond;
    private final int castCooldownTicks;

    MobPowerTier(float maxEnergy, float energyRegenPerSecond, int castCooldownTicks) {
        this.maxEnergy = maxEnergy;
        this.energyRegenPerSecond = energyRegenPerSecond;
        this.castCooldownTicks = castCooldownTicks;
    }

    /** The size of this tier's magic reserve - see SpellcastingMob.maxMysticalEnergy() / MobStaminaScaling. */
    public float maxEnergy() {
        return maxEnergy;
    }

    /** How much reserve regenerates per second (20 ticks) while below max. */
    public float energyRegenPerSecond() {
        return energyRegenPerSecond;
    }

    /** Baseline ticks between cast attempts, before the small random jitter SpellcasterState.resetCooldown adds. */
    public int castCooldownTicks() {
        return castCooldownTicks;
    }

    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MobPowerTier deserialize(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return APPRENTICE;
        }
    }
}
