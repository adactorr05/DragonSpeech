package com.dragonspeech.api;

import com.dragonspeech.mind.DuelPhase;
import com.dragonspeech.mind.SentienceTier;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Everything an addon's {@link MindDuelBrain} gets to see for one entity,
 * for one AI pulse - the same information MobMindCombatAI's own built-in
 * heuristic already bases its decision on, nothing more. Deliberately a
 * plain record of read accessors rather than the real ActiveMindDuel/
 * TeamMindDuel object itself: those classes are free to keep changing shape
 * internally (new fields, renamed methods) without ever breaking an addon
 * built against this record, as long as MobMindCombatAI keeps constructing
 * one of these the same way each pulse.
 *
 * Real domain types (SentienceTier, DuelPhase, DuelAction) are passed
 * through directly rather than re-wrapped - per the design doc's own
 * framing, this addon already "hooks directly into DragonSpeech internals
 * (BarType, DuelAction, SentienceTier...)," so hiding those specific
 * vocabulary types behind yet another abstraction would fight the doc's own
 * intent rather than serve it. What's being kept stable here is the ENTRY
 * POINT and the shape of a single pulse's information, not every type it
 * touches.
 */
public record MindDuelPulseContext(
        LivingEntity actor,
        SentienceTier actorTier,
        boolean actorIsAttacker,
        UUID opponentId,
        DuelPhase phase,
        float actorTargetBarrierIntegrity,
        float actorOwnBarrierIntegrity
) {
}
