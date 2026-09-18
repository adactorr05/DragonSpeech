package com.dragonspeech.mind;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * REDESIGNED per explicit follow-up direction - this used to reveal the
 * loser's full true name outright the instant a hugbinda-holding winner
 * won a duel. That's gone. Winning a duel now just UNLOCKS a permanent
 * "true name card" entry for that target (any win counts, no skill
 * gate at this step) - a Grimoire-listed target you can attempt to
 * learn the actual name of, one letter at a time, by casting mind-words
 * from the guessing screen. This is genuinely harder and more
 * deliberate than the old version: winning is now the START of the
 * challenge, not the whole thing.
 *
 * TWO SEPARATE UNLOCK TRIGGERS now, both calling the same
 * unlockProgress() (extracted specifically so neither one duplicates
 * the other's logic):
 *
 *   1. onDuelEnded() - MindDuelService.END_LISTENERS, fires when a duel
 *      formally ends (covers every path: fled, timed out, physical
 *      interruption, etc).
 *   2. MindDuelActionService.afterBarrierChange() calls
 *      unlockProgress() DIRECTLY the moment a defender's barrier
 *      breaks and OCCUPIED_MIND phase begins - i.e. the instant you
 *      actually gain access to their mind, which is BEFORE the duel
 *      formally ends (you can keep issuing commands for a while yet).
 *      This is what makes the true-name card show up immediately in
 *      the Occupied Mind screen instead of only after the whole duel
 *      wraps up - per explicit direction, the card belongs there too,
 *      not only in the Grimoire after the fact.
 *
 * unlockProgress() is idempotent (checks progressFor() first) so
 * calling it from BOTH triggers for the same duel never double-unlocks
 * or overwrites in-progress letter collection.
 */
public final class TrueNameDuelHooks {

    private TrueNameDuelHooks() {}

    public static void register() {
        MindDuelService.END_LISTENERS.add(TrueNameDuelHooks::onDuelEnded);
    }

    private static void onDuelEnded(MinecraftServer server, ActiveMindDuel duel, DuelOutcome outcome) {
        UUID winnerId = switch (outcome) {
            case ATTACKER_VICTORY -> duel.attackerId();
            case DEFENDER_VICTORY -> duel.defenderId();
            default -> null; // a draw or an interruption settles nothing - neither side unlocks anything from it
        };
        if (winnerId == null) {
            return;
        }
        UUID loserId = winnerId.equals(duel.attackerId()) ? duel.defenderId() : duel.attackerId();
        unlockProgress(server, winnerId, loserId);
    }

    /**
     * Unlocks a true-name-progress entry for `learnerId` about
     * `targetId`, if `learnerId` is an online player and doesn't
     * already have one (or already fully knows the name). Safe to call
     * from multiple trigger points - see class doc.
     */
    public static void unlockProgress(MinecraftServer server, UUID learnerId, UUID targetId) {
        // Only a PLAYER has a Grimoire to list this on - a mob winning a
        // duel (the system supports mob-vs-mob/mob-initiated duels
        // elsewhere) has nowhere to record it.
        ServerPlayer learner = server.getPlayerList().getPlayer(learnerId);
        if (learner == null) {
            return;
        }

        LivingEntity target = EntityLookup.byUUID(server, targetId) instanceof LivingEntity living ? living : null;
        if (target == null || target.getUUID().equals(learner.getUUID())) {
            return;
        }
        if (target instanceof com.dragonspeech.eldunari.DragonHeartVesselEntity) {
            // FIX: a Dragon Heart vessel is technically a LivingEntity
            // (see its own class doc for why), but it's a transient
            // duel-only stand-in discarded the instant the duel ends -
            // it has no true name to learn and no Grimoire entry should
            // ever be created for it. Without this check, winning a
            // Dragon Heart duel ALSO unlocked a meaningless true-name
            // card for the vessel, which is what was opening the wrong
            // screen (TrueNameGuessingScreen instead of the Dragon Heart
            // screen) when clicked from the Grimoire afterward.
            return;
        }

        PlayerMindData data = MindDataAccess.get(learner);
        if (data.progressFor(targetId).isPresent() || TrueNameService.knowsEntity(learner, target)) {
            return; // already unlocked or already fully known - nothing new to announce
        }

        MindDataAccess.set(learner, data.withTrueNameProgress(TrueNameProgress.unlocked(targetId)));
        TrueNameProgressService.sync(server, learner);
        learner.sendSystemMessage(Component.literal(
            "Something of their mind lingers with you now - a thread you could follow, given time and the right words."));
    }
}
