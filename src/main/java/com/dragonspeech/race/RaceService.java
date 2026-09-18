package com.dragonspeech.race;

import com.dragonspeech.word.Domain;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns a race's affinity domains into the same kind of cost multiplier
 * AttunementService produces - the two stack multiplicatively in
 * CastRequestHandler, so a Dwarf with deep Earth attunement is cheaper
 * still than a Dwarf without it, never a flat replacement for practice.
 */
public final class RaceService {

    /** A flat discount in an affinity domain - smaller than attunement's ceiling (50%) so mastery still matters more than birth. */
    private static final float AFFINITY_DISCOUNT = 0.20f;

    private RaceService() {}

    public static float costMultiplier(ServerPlayer player, Domain domain) {
        return RaceAccess.get(player).race()
            .filter(race -> race.affinities().contains(domain))
            .map(race -> 1.0f - AFFINITY_DISCOUNT)
            .orElse(1.0f);
    }
}
