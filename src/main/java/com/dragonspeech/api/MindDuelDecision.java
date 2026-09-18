package com.dragonspeech.api;

/**
 * What an addon's {@link MindDuelBrain} decided to do for one AI pulse.
 * Deliberately mirrors the ONLY two things MobMindCombatAI's own built-in
 * heuristic ever does per pulse - a Defense Breach crack-click attempt (0+
 * times), or a single DuelAction with a param - rather than inventing a
 * broader action vocabulary the base mod doesn't itself have a use for yet.
 * Keeping this to exactly what the base mod already does is what makes it
 * "small and stable": every field here is something MobMindCombatAI already
 * knows how to execute, today, unchanged.
 *
 * Exactly one of "crack clicks" or "perform an action" applies at a time -
 * a brain called during Defense Breach should return crackClicks(n), and a
 * brain called after the barrier's broken should return performAction(...).
 * MobMindCombatAI only ever calls a brain during the phase that action makes
 * sense for, so this isn't something a brain needs to self-police.
 */
public record MindDuelDecision(int crackClickAttempts, com.dragonspeech.mind.DuelAction action, String actionParam) {

    /** "I have nothing to do this pulse - fall back to the base mod's own heuristic for just this tick." Not the same as permanently un-registering a brain; a brain can PASS on some pulses and act on others. */
    public static final MindDuelDecision PASS = new MindDuelDecision(0, null, null);

    /** Defense Breach phase: attempt this many crack-clicks this pulse (same mechanism clickAttemptsPerPulse already drives for the built-in heuristic). 0 is valid and equivalent to PASS for this pulse. */
    public static MindDuelDecision crackClicks(int attempts) {
        return new MindDuelDecision(Math.max(0, attempts), null, null);
    }

    /** Post-breach phase: issue this DuelAction (see MindDuelActionService for the registered action set and what each param means) as this pulse's move. */
    public static MindDuelDecision performAction(com.dragonspeech.mind.DuelAction action, String param) {
        return new MindDuelDecision(0, action, param);
    }

    public boolean isPass() {
        return crackClickAttempts <= 0 && action == null;
    }
}
