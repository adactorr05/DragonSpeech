package com.dragonspeech.api;

import com.dragonspeech.mind.SentienceTier;
import net.minecraft.world.entity.LivingEntity;

/**
 * The base mod's small, stable extension point for mind-duel AI, per the
 * Neural Network addon's own design doc, section 9: "BASE MOD gets a
 * small, stable extension point: a registrable 'brain' interface that
 * MobMindCombatAI checks first and falls back to its current heuristic if
 * none is registered." This interface, {@link MindDuelBrainRegistry}, and
 * {@link MindDuelPulseContext}/{@link MindDuelDecision} ARE that pass's
 * entire scope - full entity behavior (movement/spellcasting/targeting)
 * is explicitly a separate, later hook, not part of this one.
 *
 * SERVER-SIDE ONLY. Mind-duel AI runs entirely server-side (MobMindCombatAI
 * itself only ever runs from a server tick), so a brain implementation
 * should never assume client state is available - an addon's own GUI/edit
 * flow is a completely separate, client-side concern that syncs its result
 * to the server over its own payloads, the same pattern this base mod's own
 * config screen already uses (see ConfigScreen/ConfigRequestHandler).
 *
 * ONE BRAIN AT A TIME (see MindDuelBrainRegistry) - this is intentionally
 * not a multi-brain pipeline. There's exactly one addon this exists for
 * today, and a single registration slot is the smaller, more stable
 * surface; if a second AI addon ever needs to coexist with this one, that's
 * a real design problem worth solving deliberately then, not something to
 * guess a solution for now with nothing real to test it against.
 *
 * SCOPE NOTE - 1v1 duels only (ActiveMindDuel), NOT team duels
 * (TeamMindDuel): team duels use a genuinely different shape (TeamDuelAction
 * instead of DuelAction, linkStrength() instead of barrier integrity, no
 * DuelPhase at all) - cramming both into one context/decision pair would
 * mean half the fields are meaningless depending on which kind of duel
 * called in, which is exactly the kind of ambiguous, easy-to-misuse surface
 * this pass is trying to avoid. A TeamMindDuelBrain hook can be added
 * later, mirroring this same pattern, once there's a real addon to build it
 * against - MobMindCombatAI's maybeActTeamAttacker() is untouched for now.
 */
public interface MindDuelBrain {

    /**
     * Checked once per pulse, before decide() - lets a brain opt in only for
     * specific entities/tiers (e.g. only entities that actually have a
     * saved network configured) while staying registered globally. A brain
     * that always returns true here is simply always in control once
     * registered, for every entity capable of acting in a duel at all
     * (see SentienceTier#canActInDuel()) - the same entities MobMindCombatAI's
     * built-in heuristic already governs.
     */
    boolean handles(LivingEntity actor, SentienceTier tier);

    /**
     * Called once per AI pulse (same cadence the base mod's own heuristic
     * already runs on - see MobMindCombatAI's own ACT_PULSE_TICKS) for an
     * entity this brain accepted via handles(). Return {@link MindDuelDecision#PASS}
     * to defer to the base mod's built-in heuristic for JUST this pulse -
     * a brain doesn't have to have an opinion on every single tick.
     */
    MindDuelDecision decide(MindDuelPulseContext context);

    /**
     * Checked once per NEARBY, duel-capable, not-currently-duelling mob per
     * initiate-pulse (a much slower cadence than decide() - see
     * MobMindCombatAI's own INITIATE_PULSE_TICKS), only for mobs this brain
     * accepted via handles(). Return TRUE to have this mob attempt to start
     * a duel against the given player right now (the attempt still goes
     * through ContactResolver.attemptAsMob(), which can still refuse for
     * its own reasons - returning true isn't a guarantee a duel actually
     * starts). Return FALSE to explicitly suppress the built-in random-
     * chance roll for this mob this pulse (a "no, not now" from the brain).
     * Return NULL (the default) to defer entirely to the base mod's own
     * flat-chance heuristic, unchanged - an addon that only cares about
     * IN-DUEL decisions never has to implement this at all.
     */
    default Boolean shouldInitiateDuel(LivingEntity mob, SentienceTier tier, net.minecraft.server.level.ServerPlayer nearbyPlayer) {
        return null;
    }

    /**
     * Called once, for either participant this brain accepted via
     * handles(), when a duel they were in ends - forwarded straight from
     * MindDuelService.END_LISTENERS (the same mechanism DragonHeartService
     * and friends already use for exactly this purpose), so `outcome` is
     * the real, final DuelOutcome (ATTACKER_VICTORY / DEFENDER_VICTORY /
     * DRAW_EXHAUSTION / INTERRUPTED), not a guess. This is the hook a
     * Learning/Reward/Battle-Review block would trigger off of. Default
     * no-op - a brain that doesn't care about post-duel learning doesn't
     * have to implement this.
     */
    default void onDuelEnded(LivingEntity actor, SentienceTier actorTier, com.dragonspeech.mind.DuelOutcome outcome) {
    }
}
