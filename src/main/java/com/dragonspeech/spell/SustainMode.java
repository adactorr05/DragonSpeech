package com.dragonspeech.spell;

/**
 * How a created magical construct continues to exist after the initial cast.
 *
 * DURATION  - default when neither afla nor aflbinda is spoken. The construct
 *             has no durability pool; it simply exists until its timer ends.
 * RESERVE   - `afla`: the construct owns a finite magical reserve. Impacts/use
 *             consume that reserve, but time itself does not.
 * CASTER    - `aflbinda`: the construct is tied directly to the caster's live
 *             stamina. It has no independent reserve and collapses when the
 *             caster can no longer sustain it.
 */
public enum SustainMode {
    DURATION,
    RESERVE,
    CASTER;

    public static SustainMode from(SpellComposition composition) {
        if (composition.occurrencesOf("aflbinda") > 0) return CASTER;
        if (composition.occurrencesOf("afla") > 0) return RESERVE;
        return DURATION;
    }
}
