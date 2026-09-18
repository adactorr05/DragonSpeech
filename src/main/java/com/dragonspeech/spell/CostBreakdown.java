package com.dragonspeech.spell;

/**
 * Every factor that went into a spell's final cost, exposed so the
 * (future) casting grid UI can show players WHY a spell costs what it
 * does - e.g. "no scope word: cost x1.75" is exactly the kind of feedback
 * that teaches players to write better spells instead of just punishing
 * them with an opaque number. Also very useful for your own balance testing.
 */
public record CostBreakdown(
    float finalCost,
    float precisionMultiplier,
    float scopeAmbiguityPenalty,
    float modifierFactor,
    float distanceFactor,
    float permanenceFactor,
    float scopeSizeFactor,
    float attunementFactor,
    float realityDomainFactor
) {
    public String describe() {
        return String.format(
            "cost=%.2f (precision x%.2f, scope-ambiguity x%.2f, modifiers x%.2f, distance x%.2f, permanence x%.2f, targets x%.2f, attunement x%.2f, reality x%.2f)",
            finalCost, precisionMultiplier, scopeAmbiguityPenalty, modifierFactor,
            distanceFactor, permanenceFactor, scopeSizeFactor, attunementFactor, realityDomainFactor
        );
    }
}
