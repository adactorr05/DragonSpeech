package com.dragonspeech.mind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks ongoing TeamMindDuels - mirrors MindDuelManager's shape exactly.
 * A participant (attacker or any defender) can only be in one duel of
 * EITHER kind at a time - see the cross-checks in ContactResolver and
 * TeamContactResolver, which both consult MindDuelManager.isInDuel() too.
 */
public final class TeamMindDuelManager {

    private static final Map<UUID, TeamMindDuel> DUELS_BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> PARTICIPANT_TO_DUEL = new HashMap<>();

    private TeamMindDuelManager() {}

    public static TeamMindDuel start(TeamMindDuel duel) {
        DUELS_BY_ID.put(duel.duelId(), duel);
        PARTICIPANT_TO_DUEL.put(duel.attackerId(), duel.duelId());
        for (UUID memberId : duel.defenders().keySet()) {
            PARTICIPANT_TO_DUEL.put(memberId, duel.duelId());
        }
        return duel;
    }

    public static Optional<TeamMindDuel> forParticipant(UUID entityId) {
        UUID duelId = PARTICIPANT_TO_DUEL.get(entityId);
        return duelId == null ? Optional.empty() : Optional.ofNullable(DUELS_BY_ID.get(duelId));
    }

    public static boolean isInDuel(UUID entityId) {
        return PARTICIPANT_TO_DUEL.containsKey(entityId);
    }

    public static void end(UUID duelId) {
        TeamMindDuel removed = DUELS_BY_ID.remove(duelId);
        if (removed == null) {
            return;
        }
        PARTICIPANT_TO_DUEL.remove(removed.attackerId());
        for (UUID memberId : removed.defenders().keySet()) {
            PARTICIPANT_TO_DUEL.remove(memberId);
        }
    }

    public static List<TeamMindDuel> allActive() {
        return List.copyOf(DUELS_BY_ID.values());
    }
}
