package com.dragonspeech.mind;

/**
 * How a duel ended - drives which flavor message, cooldown, and mental
 * fatigue MindDuelService.end() applies to each side.
 */
public enum DuelOutcome {
    /** Defender's Focus or Stamina hit 0, or a command went uncontested to completion. */
    ATTACKER_VICTORY,
    /** Attacker was forced out - their Focus or Stamina hit 0, or they disengaged. */
    DEFENDER_VICTORY,
    /** Both sides exhausted each other roughly simultaneously. Both are pushed out and weakened. */
    DRAW_EXHAUSTION,
    /** Physical damage (to either side) broke concentration hard enough to end the duel outright. */
    INTERRUPTED
}
