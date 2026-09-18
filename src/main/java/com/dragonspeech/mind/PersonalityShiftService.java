package com.dragonspeech.mind;

import com.dragonspeech.growth.AttunementAccess;
import com.dragonspeech.growth.PlayerAttunementData;
import com.dragonspeech.word.Domain;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/**
 * The design notes say a true name "can be changed if a specific aspect
 * of the player's personality is changed" but never pin down what that
 * means mechanically. This is the concrete answer this codebase picks:
 * a caster's DOMINANT domain (whichever they've grown the most practiced
 * in) is treated as the closest thing to a measurable personality this
 * mod tracks - fire-dominant, life-dominant, mind-dominant, and so on
 * are genuinely different kinds of people. When that dominant domain
 * changes, their true name changes with it.
 *
 * Call checkForShift() from wherever Attunement XP is granted
 * (AttunementService.grantExperience) - it's cheap (one map scan) and
 * only ever fires TrueNameService.regenerate() on a genuine change, not
 * on every single cast.
 */
public final class PersonalityShiftService {

    /** A domain only counts as "dominant" once it's clearly ahead - avoids noisy flips during early, evenly-spread leveling. */
    private static final float MIN_ATTUNEMENT_TO_COUNT = 15f;

    private PersonalityShiftService() {}

    public static void checkForShift(ServerPlayer player) {
        Optional<Domain> currentDominant = dominantDomain(player);
        if (currentDominant.isEmpty()) {
            return;
        }

        PlayerMindData data = MindDataAccess.get(player);
        Optional<Domain> lastKnown = data.lastDominantDomain();

        if (lastKnown.isEmpty()) {
            // First time we've ever been able to determine a dominant domain -
            // just record it. This is a baseline, not a "shift".
            MindDataAccess.set(player, data.withLastDominantDomain(currentDominant.get()));
            return;
        }

        if (lastKnown.get() != currentDominant.get()) {
            MindDataAccess.set(player, data.withLastDominantDomain(currentDominant.get()));
            TrueNameService.regenerate(player);
            player.sendSystemMessage(Component.literal(
                "Something in you has shifted, deeply enough to matter. Whatever you knew of your own true name, and whatever anyone else knew of it, is no longer so."));
        }
    }

    private static Optional<Domain> dominantDomain(ServerPlayer player) {
        PlayerAttunementData attunement = AttunementAccess.get(player);
        Domain best = null;
        float bestValue = MIN_ATTUNEMENT_TO_COUNT;
        for (Map.Entry<Domain, Float> entry : attunement.attunement().entrySet()) {
            if (entry.getValue() > bestValue) {
                bestValue = entry.getValue();
                best = entry.getKey();
            }
        }
        return Optional.ofNullable(best);
    }
}
