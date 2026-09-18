package com.dragonspeech.spell;

/**
 * The shared price curve for saying the same word more than once in one
 * sentence (margfalt margfalt, afla afla afla, ...). This is deliberately
 * NOT the same thing SpellCostCalculator forbids ("word count never
 * appears in the formula") - that rule is about a sentence's total word
 * count, which should never by itself make a spell cost more. This is
 * narrower and opt-in: a caster who repeats one specific word is asking
 * for more of exactly what that word grants, and paying for it should
 * reflect that they're overbuilding, not just speaking at length.
 *
 * The benefit of repeating a word stays LINEAR (2x affects it twice as
 * much as 1x) - simple and predictable. The COST of getting there is
 * SUPER-linear, so stacking is genuinely powerful but genuinely
 * inefficient, never a free lunch:
 *
 *   1x -> x1.0   2x -> x2.4   3x -> x4.2   4x -> x6.4   5x -> x9.0
 *
 * Every EffectHandler or grammar path that wants to support "say it
 * again for more" should read the plain occurrence count from
 * SpellComposition.occurrencesOf(...) for the BENEFIT (scale it flatly
 * by n) and this multiplier for the COST.
 */
public final class RepetitionCost {

    private RepetitionCost() {}

    public static float multiplier(int occurrences) {
        int n = Math.max(1, occurrences);
        return n + 0.2f * n * (n - 1);
    }
}
