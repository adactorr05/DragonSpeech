package com.dragonspeech.mob.casting;

import java.util.UUID;

/**
 * "Manage between defense, offense, and their healing/wards/utility...
 * more spells faster if pressured, steady management otherwise" per
 * explicit direction. Before this existed, a mob spent energy the
 * instant it had enough for ANYTHING, every single cooldown cycle -
 * "use up all their stamina as fast as they can, then spam spells as
 * soon as they have the stamina" (the composer would even keep trying
 * and failing once the pool was too depleted to afford anything, since
 * nothing previously stopped it from attempting).
 *
 * Four strategies, chosen fresh each cast attempt from two inputs:
 * - pressureFraction() (see SpellcasterState) - how hard this mob is
 *   currently being hit. High pressure always wins and forces AGGRESSIVE
 *   regardless of personality, matching "if they are being attacked
 *   harshly... more spells faster."
 * - a per-mob personality roll, stable for that individual's lifetime
 *   (seeded from its own UUID, not the shared world random) - so not
 *   every Elf in a group behaves identically, and the same individual
 *   doesn't flip-flop between DEFENSIVE and BALANCED tick to tick when
 *   pressure is low.
 *
 * Each strategy sets a RESERVE fraction of max energy the mob won't
 * spend below (except AGGRESSIVE, which spends freely) - see
 * MobSpellCastGoal, which computes an energy BUDGET from this and
 * passes it through to MobCastExecutor/MobSpellComposer instead of the
 * mob's full current energy.
 */
public enum MobCastStrategy {
    /** "More spells faster" - no reserve, spend whatever's affordable right now. */
    AGGRESSIVE(0f, false),
    /** Default middle ground - keep a modest cushion. */
    BALANCED(0.15f, false),
    /** "Steady management" - keep a healthy cushion, don't chase every affordable cast. */
    STEADY(0.30f, false),
    /** "More defense" - the biggest cushion, and healing/crowd-control get tried before offense. */
    DEFENSIVE(0.40f, true);

    private final float reserveFraction;
    private final boolean preferDefenseFirst;

    MobCastStrategy(float reserveFraction, boolean preferDefenseFirst) {
        this.reserveFraction = reserveFraction;
        this.preferDefenseFirst = preferDefenseFirst;
    }

    public float reserveFraction() {
        return reserveFraction;
    }

    public boolean preferDefenseFirst() {
        return preferDefenseFirst;
    }

    private static final float PRESSURE_THRESHOLD = 0.55f;

    public static MobCastStrategy choose(SpellcastingMob caster) {
        float pressure = caster.spellState().pressureFraction();
        if (pressure >= PRESSURE_THRESHOLD) {
            return AGGRESSIVE;
        }
        float personality = personalityRoll(caster.asEntity().getUUID());

        // "Shades are meant to be extremely difficult to beat" per
        // explicit direction - a Shade leans noticeably more aggressive/
        // confident than the other races, rarely playing purely
        // defensive, reflecting its raw power advantage rather than
        // needing to husband resources as carefully.
        if (caster instanceof com.dragonspeech.shade.ShadeEntity) {
            if (personality < 0.05f) {
                return DEFENSIVE;
            }
            if (personality < 0.25f) {
                return STEADY;
            }
            return BALANCED;
        }

        if (personality < 0.22f) {
            return DEFENSIVE;
        }
        if (personality < 0.60f) {
            return STEADY;
        }
        return BALANCED;
    }

    /** Stable 0-1 value per mob individual, independent of the world's shared random and of pressure - a fixed "temperament" for that mob's whole lifetime. */
    private static float personalityRoll(UUID id) {
        long bits = id.getLeastSignificantBits();
        return (float) ((bits & 0xFFFFL) / 65536.0);
    }
}
