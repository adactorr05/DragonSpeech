package com.dragonspeech.grid;

import java.util.Optional;

/**
 * Checks whether a CastingGridState forms a legal spell before it's ever
 * sent to SpellCastResolver. The grid's fixed slot layout already prevents
 * most nonsense - you physically can't place two verbs, since there's only
 * one VERB slot - so this mostly just confirms a verb is actually present.
 * More elaborate grammar rules can grow here later without touching
 * anything downstream.
 */
public final class GrammarValidator {

    private GrammarValidator() {}

    public static Optional<String> validate(CastingGridState state) {
        if (!state.assignments().containsKey(GridSlot.VERB)) {
            return Optional.of("A spell needs a verb.");
        }
        return Optional.empty();
    }
}
