package com.dragonspeech.mind;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Orchestration that doesn't belong to any single phase: ending a duel
 * (from any phase, for any reason) and reacting to physical damage taken
 * by a participant while a duel is open (design notes, "Interruptions" -
 * present in nearly every phase diagram). Both ContactResolver and
 * MindDuelActionService call into end() rather than tearing down a duel
 * themselves, so mental fatigue and the ended-event fire exactly once,
 * from exactly one place, no matter which phase or action ended things.
 */
public final class MindDuelService {

    /** Flat Focus damage from a single hit while a duel is open, before Discipline reduces it - only still used by the team-duel defender accumulation path now (see onPhysicalDamageTeam). */
    private static final float INTERRUPTION_FOCUS_DAMAGE = 12f;
    private static final int MENTAL_FATIGUE_DURATION_TICKS = 20 * 45;

    /**
     * Extension point, same spirit as MindFortitudeService.TIER_OVERRIDES:
     * anything that needs to react to a duel's final outcome without this
     * file knowing it exists registers a listener here rather than getting
     * a bespoke call added to end() for every new consumer. Added for the
     * Dragon Heart mind-duel (permission/broken state is written onto the item
     * from here - see com.dragonspeech.eldunari.DragonHeartService), but
     * generic for any future "something happened, a duel just decided it"
     * system. Listeners run BEFORE this duel is removed from
     * MindDuelManager but AFTER its phase is set to ENDED, so
     * duel.attacker()/defender() pools still reflect final state.
     */
    public static final java.util.List<MindDuelEndListener> END_LISTENERS = new java.util.ArrayList<>();

    private MindDuelService() {}

    /** Call whenever a duel participant takes physical damage. Safe to call for entities not in a duel - it's a no-op. */
    /**
     * REDESIGNED per explicit direction - this used to only chip away at
     * the hit combatant's FOCUS (mitigated by their own discipline), and
     * only actually ended the duel once focus fully broke OR enough
     * separate hits accumulated to hit INTERRUPTIONS_TO_END - and THAT
     * path ended the duel as a neutral draw (DuelOutcome.INTERRUPTED,
     * "both minds are shaken loose"), not a win for either side. The
     * explicit complaint: taking damage was disengaging BOTH
     * combatants, not just declaring a winner. Now: any physical damage
     * to either participant ends the duel immediately, and the OTHER
     * side wins outright - "ONLY the one that takes damage... loses the
     * duel," letting the winner take control of the loser, no draw
     * state reachable from physical damage at all anymore.
     */
    public static void onPhysicalDamage(MinecraftServer server, LivingEntity entity) {
        ActiveMindDuel duel = MindDuelManager.forParticipant(entity.getUUID()).orElse(null);
        if (duel == null || duel.phase() == DuelPhase.ENDED) {
            return;
        }

        boolean isAttacker = duel.isAttacker(entity.getUUID());
        DuelOutcome outcome = isAttacker ? DuelOutcome.DEFENDER_VICTORY : DuelOutcome.ATTACKER_VICTORY;
        String message = isAttacker
            ? "A blow lands on the attacker in the physical world - their hold on the link breaks, and the defender seizes it."
            : "A blow lands on the defender in the physical world - their mind falters, and the attacker seizes control.";
        end(server, duel, outcome, message);
    }

    /** Team-duel counterpart of onPhysicalDamage() above. The ATTACKER side gets the same "any hit ends it, instantly, in the defenders' favor" rule as 1v1 - being the sole attacker, there's no team nuance to preserve there. The DEFENDER side keeps its accumulation toward isTeamDefeated() (defeating a whole linked team is a genuinely different mechanic from single-combatant disengagement, and shouldn't collapse into an instant loss for the whole team over one member taking one hit) - but the neutral "draw" path (interruptionsThisPhase reaching a cap) is removed here too, same as 1v1: a hit that doesn't defeat the team just doesn't end anything by itself. */
    public static void onPhysicalDamageTeam(MinecraftServer server, LivingEntity entity) {
        TeamMindDuel duel = TeamMindDuelManager.forParticipant(entity.getUUID()).orElse(null);
        if (duel == null || duel.ended()) {
            return;
        }

        boolean isAttacker = duel.isAttacker(entity.getUUID());

        if (isAttacker) {
            TeamMindDuelService.end(server, duel, DuelOutcome.DEFENDER_VICTORY,
                "A blow lands on the attacker in the physical world - their hold on the link breaks, and the defenders seize it.");
            return;
        }

        MindCombatant hit = duel.defender(entity.getUUID());
        if (hit == null) {
            return;
        }

        float mitigated = Math.max(2f, INTERRUPTION_FOCUS_DAMAGE - hit.discipline() * 0.15f);
        hit.damageFocus(mitigated);
        duel.addInterruption();

        if (duel.isTeamDefeated()) {
            TeamMindDuelService.end(server, duel, DuelOutcome.ATTACKER_VICTORY,
                "A blow lands in the physical world, and the last of the linked defenders falls silent.");
            return;
        }

        TeamMindDuelSyncHooks.pushSync(server, duel);
    }

    public static void end(MinecraftServer server, ActiveMindDuel duel, DuelOutcome outcome, String message) {
        if (duel.phase() == DuelPhase.ENDED) {
            return; // already ended by another path this same tick - don't double-apply fatigue
        }
        duel.setPhase(DuelPhase.ENDED);
        for (var listener : END_LISTENERS) {
            listener.onDuelEnded(server, duel, outcome);
        }
        MindDuelManager.end(duel.duelId());
        // Control has its own independent lifecycle (its own timeout, its
        // own resistance checks) and was never guaranteed to be torn down
        // just because the underlying duel ended - Disengage in
        // particular would leave the attacker stuck possessing the
        // target's body with no way back. Ending the duel now always
        // ends Control too, regardless of which path got here.
        com.dragonspeech.mind.MindControlService.endControlForTarget(server, duel.defenderId());

        ServerPlayer attacker = server.getPlayerList().getPlayer(duel.attackerId());
        ServerPlayer defender = server.getPlayerList().getPlayer(duel.defenderId());

        if (attacker != null) {
            attacker.sendSystemMessage(Component.literal(message));
            applyMentalFatigue(attacker, outcome == DuelOutcome.ATTACKER_VICTORY);
        }
        if (defender != null) {
            defender.sendSystemMessage(Component.literal(message));
            applyMentalFatigue(defender, outcome == DuelOutcome.DEFENDER_VICTORY);
        }
    }

    /** Winning still has a cost (design notes, "Mental Fatigue") - the loser just gets more of it. Package-visible so TeamMindDuelService can reuse the exact same fatigue logic instead of duplicating it. */
    static void applyMentalFatigue(ServerPlayer player, boolean won) {
        int amplifier = won ? 0 : 1;
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, MENTAL_FATIGUE_DURATION_TICKS, amplifier, false, true));
        if (!won) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, MENTAL_FATIGUE_DURATION_TICKS / 3, 0, false, true));
        }
    }
}
