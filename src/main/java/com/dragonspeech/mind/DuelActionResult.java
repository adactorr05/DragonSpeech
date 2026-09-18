package com.dragonspeech.mind;

import java.util.Optional;

/**
 * What happened when a DuelAction was resolved. duelEnded + outcome are
 * only present when this action was the one that finished the duel -
 * MindDuelService.end() will already have been called by the time the
 * caller sees this, so the caller only needs to relay the result.
 */
public record DuelActionResult(boolean legal, String message, boolean duelEnded, Optional<DuelOutcome> outcome) {

    public static DuelActionResult illegal(String message) {
        return new DuelActionResult(false, message, false, Optional.empty());
    }

    public static DuelActionResult ok(String message) {
        return new DuelActionResult(true, message, false, Optional.empty());
    }

    public static DuelActionResult ended(String message, DuelOutcome outcome) {
        return new DuelActionResult(true, message, true, Optional.of(outcome));
    }
}
