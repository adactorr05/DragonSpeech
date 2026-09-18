package com.dragonspeech.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A temporary "cannot cast" flag, checked by CastRequestHandler before
 * anything else happens in handleComposition(). This is the hook that
 * was previously just a TODO in docs/MIND_DUEL_PHASE6.md ("a 'disable
 * spellcasting' command needs a hook into CastRequestHandler that
 * doesn't exist yet") - now that hook exists, and CommandEffectRegistry's
 * "silence" command effect uses it.
 *
 * Deliberately NOT part of PlayerMagicData/PlayerSkills - this is a
 * short-lived combat debuff, not persistent state, so it doesn't need
 * to survive a restart or be codec'd at all.
 */
public final class MindSilence {

    private static final Map<UUID, Long> SILENCED_UNTIL = new HashMap<>();

    private MindSilence() {}

    public static void silence(UUID playerId, long untilGameTime) {
        SILENCED_UNTIL.put(playerId, untilGameTime);
    }

    public static boolean isSilenced(UUID playerId, long now) {
        Long until = SILENCED_UNTIL.get(playerId);
        return until != null && now < until;
    }
}
