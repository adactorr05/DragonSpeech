package com.dragonspeech.spell;

/**
 * Turns a SpellComposition (the words used) plus an EffortContext (what's
 * actually being attempted) into a final stamina cost.
 *
 * THE ONE RULE THIS FILE EXISTS TO PROTECT: word COUNT never appears
 * anywhere in this formula. A forty-word, perfectly precise spell should
 * come out cheaper than a two-word vague one aimed at the same task -
 * length is a tool players use to narrow and cheapen an effect, never a
 * cost multiplier in itself. If you ever find yourself wanting to add
 * "* words.size()" somewhere, that's a sign the design has drifted from
 * the source material - fix the underlying baseTaskMagnitude or precision
 * values instead.
 *
 * Every constant below is a balance knob, not a rule - tune freely as you
 * playtest. Nothing here is final.
 */
public final class SpellCostCalculator {

    // --- Tunable balance constants -------------------------------------

    /** How strongly higher average precision discounts the base cost. 0 = no effect, 1 = full effect. */
    private static final float PRECISION_DISCOUNT_STRENGTH = 0.85f;

    /** Even a perfectly precise spell still costs at least this fraction of its base task magnitude. */
    private static final float PRECISION_DISCOUNT_FLOOR = 0.15f;

    /** Cost multiplier applied when a spell has NO scope word at all - the engine assumes worst case. */
    private static final float MAX_SCOPE_AMBIGUITY_PENALTY = 1.75f;

    /** Cost added per block of distance between caster and target. */
    private static final float DISTANCE_COST_PER_BLOCK = 0.01f;

    /** Distance cost stops scaling past this range, to avoid absurd numbers for far-off targets. */
    private static final float MAX_DISTANCE_FOR_COST = 100f;

    /** Multiplier applied when the caster intends the effect to persist (wards, transmutation, etc.). */
    private static final float PERMANENT_EFFECT_MULTIPLIER = 3.0f;

    /** Extra cost multiplier per additional target beyond the first. */
    private static final float COST_PER_EXTRA_TARGET = 0.5f;

    /** Absolute minimum cost of any spell, however cheap the math works out - nothing is ever free. */
    private static final float MINIMUM_SPELL_COST = 0.5f;

    private SpellCostCalculator() {}

    /**
     * @param composition             the words used, already grammar-validated
     * @param context                 what's actually being attempted (from the effect handler)
     * @param domainAttunementMultiplier a caller-supplied discount from the caster's Attunement
     *                                 in the spell's dominant domain (Phase 4). Pass 1.0f if you
     *                                 don't have Attunement wired up yet - this calculator doesn't
     *                                 know or care where the number came from.
     */
    public static CostBreakdown calculate(SpellComposition composition, EffortContext context, float domainAttunementMultiplier) {

        float precisionMultiplier = clamp(
            1.0f - (composition.averageMagnitudePrecision() * PRECISION_DISCOUNT_STRENGTH),
            PRECISION_DISCOUNT_FLOOR,
            1.0f
        );

        float scopeAmbiguityPenalty = composition.scopeWord()
            .map(scope -> 1.0f + (1.0f - scope.precision()) * (MAX_SCOPE_AMBIGUITY_PENALTY - 1.0f))
            .orElse(MAX_SCOPE_AMBIGUITY_PENALTY);

        float modifierFactor = Math.max(1.0f + composition.modifierMagnitudeSum(), 0.05f);

        float clampedDistance = Math.min(context.distanceFromCaster(), MAX_DISTANCE_FOR_COST);
        float distanceFactor = 1.0f + (clampedDistance * DISTANCE_COST_PER_BLOCK);

        float permanenceFactor = context.permanent() ? PERMANENT_EFFECT_MULTIPLIER : 1.0f;

        int extraTargets = Math.max(context.targetCount() - 1, 0);
        float scopeSizeFactor = 1.0f + (extraTargets * COST_PER_EXTRA_TARGET);

        float attunementFactor = Math.max(domainAttunementMultiplier, 0.05f);

        // Reality-domain pressure is intrinsic to what is being asked, not to sentence length.
        // Precision can still reduce waste, but it cannot make Void/Time/Gravity/Fate as cheap as
        // ordinary fire merely by describing them carefully. Distinct dangerous domains compound
        // when woven together, capped to keep the number finite while still making combinations
        // appropriately alarming.
        float realityDomainFactor = realityDomainFactor(composition);

        float rawCost = context.baseTaskMagnitude()
            * precisionMultiplier
            * scopeAmbiguityPenalty
            * modifierFactor
            * distanceFactor
            * permanenceFactor
            * scopeSizeFactor
            * attunementFactor
            * realityDomainFactor;

        float finalCost = Math.max(rawCost, MINIMUM_SPELL_COST);

        return new CostBreakdown(
            finalCost,
            precisionMultiplier,
            scopeAmbiguityPenalty,
            modifierFactor,
            distanceFactor,
            permanenceFactor,
            scopeSizeFactor,
            attunementFactor,
            realityDomainFactor
        );
    }

    /**
     * Intrinsic effort for domains that tamper with rules reality normally enforces. These values
     * intentionally apply when the domain appears anywhere in the sentence, not only when it is
     * the first verb/domain used for attunement. That matters for constructions such as
     * "varnbinda thomr" where the action is Binding but the substance is Void.
     */
    private static float realityDomainFactor(SpellComposition composition) {
        java.util.EnumSet<com.dragonspeech.word.Domain> seen = java.util.EnumSet.noneOf(com.dragonspeech.word.Domain.class);
        float factor = 1.0f;
        for (var word : composition.words()) {
            var domain = word.domain();
            if (!seen.add(domain)) continue;
            factor *= switch (domain) {
                case GRAVITY -> 1.8f;
                case FATE -> 2.3f;
                case TIME -> 2.6f;
                case VOID -> 3.5f;
                default -> 1.0f;
            };
        }
        return Math.min(12.0f, factor);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
