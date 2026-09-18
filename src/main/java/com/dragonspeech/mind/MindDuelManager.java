package com.dragonspeech.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks every ongoing duel, at most one per participant on either side -
 * you cannot be mid-duel and also start or be pulled into a second one.
 * See ChannelManager for the equivalent pattern used elsewhere in this
 * codebase; this is deliberately the same shape.
 */
public final class MindDuelManager {

    private static final Map<UUID, ActiveMindDuel> DUELS_BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> PARTICIPANT_TO_DUEL = new HashMap<>();

    /**
     * "It should happen when I use my mind skill. Not a right click...
     * I want it to take over the Mind Duel when the command is enabled"
     * per explicit direction - see MindBreachAdmin, which used to hook
     * a separate right-click event (competing with, and sometimes
     * winning over, whatever else a right-click was also supposed to
     * do - like opening the Dragon Heart screen). Now it registers here
     * instead, the same universal point EVERY duel actually starts
     * through regardless of source (a player's own mind skill,
     * DragonHeartService.beginContact, anything else) - no separate
     * interaction hook needed at all anymore.
     */
    public static final java.util.List<java.util.function.Consumer<ActiveMindDuel>> START_LISTENERS = new java.util.ArrayList<>();

    private MindDuelManager() {}

    public static ActiveMindDuel start(ActiveMindDuel duel) {
        DUELS_BY_ID.put(duel.duelId(), duel);
        PARTICIPANT_TO_DUEL.put(duel.attackerId(), duel.duelId());
        PARTICIPANT_TO_DUEL.put(duel.defenderId(), duel.duelId());
        for (var listener : START_LISTENERS) {
            listener.accept(duel);
        }
        return duel;
    }

    public static Optional<ActiveMindDuel> forParticipant(UUID entityId) {
        UUID duelId = PARTICIPANT_TO_DUEL.get(entityId);
        return duelId == null ? Optional.empty() : Optional.ofNullable(DUELS_BY_ID.get(duelId));
    }

    public static boolean isInDuel(UUID entityId) {
        return PARTICIPANT_TO_DUEL.containsKey(entityId);
    }

    public static void end(UUID duelId) {
        ActiveMindDuel removed = DUELS_BY_ID.remove(duelId);
        if (removed != null) {
            PARTICIPANT_TO_DUEL.remove(removed.attackerId());
            PARTICIPANT_TO_DUEL.remove(removed.defenderId());
        }
    }

    /** For the tick handler - iterate over a stable snapshot since resolvers may end duels mid-iteration. */
    public static java.util.List<ActiveMindDuel> allActive() {
        return java.util.List.copyOf(DUELS_BY_ID.values());
    }
}