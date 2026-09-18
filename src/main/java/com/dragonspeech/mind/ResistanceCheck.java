package com.dragonspeech.mind;

/**
 * One formula, used by both command phases, per the design notes' own
 * table (image 5: "EXAMPLE COMMAND DIFFICULTIES, Normal vs. True Name" -
 * a "drop weapon" command goes from difficulty 20 down to ~1 once the
 * true name is known). Rather than hand-author two completely separate
 * resolution paths, TRUE_NAME_DOMINATION is modeled as an enormous
 * difficulty discount on the exact same roll OCCUPIED_MIND uses.
 */
public final class ResistanceCheck {

    /** True-name domination divides effective difficulty by this before rolling - matches the ~20x drop in the design notes' example table. */
    private static final float TRUE_NAME_DIFFICULTY_DIVISOR = 20f;

    private ResistanceCheck() {}

    /** Percent (0-100) chance the defender resists. Willpower raises it, command difficulty lowers it, true-name domination crushes it near zero. The final 0.8x applies specifically to give the ATTACKER slightly more dominance overall while a mind is merely occupied (not yet true-named) - the defender can still fight back, just not quite evenly. */
    public static float resistChance(MindCombatant defender, float commandDifficulty, boolean trueNameDomination) {
        float effectiveDifficulty = trueNameDomination ? commandDifficulty / TRUE_NAME_DIFFICULTY_DIVISOR : commandDifficulty;
        float focusFraction = defender.maxFocus() > 0 ? defender.focus() / defender.maxFocus() : 0f;

        float chance = defender.willpower() * 0.8f * focusFraction - effectiveDifficulty * 1.5f;
        chance *= 0.8f;
        return Math.max(trueNameDomination ? 0f : 3f, Math.min(95f, chance));
    }
}
