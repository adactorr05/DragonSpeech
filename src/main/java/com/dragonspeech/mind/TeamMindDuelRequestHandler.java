package com.dragonspeech.mind;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Mirrors MindDuelRequestHandler, for TeamMindDuel's single C2S payload. */
public final class TeamMindDuelRequestHandler {

    private TeamMindDuelRequestHandler() {}

    public static void handleAction(ServerPlayer player, String rawAction, UUID targetId) {
        TeamMindDuel duel = TeamMindDuelManager.forParticipant(player.getUUID()).orElse(null);
        if (duel == null) {
            return; // stale click from a screen the server already closed - not an error
        }

        TeamDuelAction action;
        try {
            action = TeamDuelAction.valueOf(rawAction.toUpperCase());
        } catch (IllegalArgumentException e) {
            return; // malformed/stale client payload - ignore rather than crash the handler
        }

        DuelActionResult result = TeamMindDuelService.resolve(player.getServer(), duel, player.getUUID(), action, targetId);
        player.sendSystemMessage(Component.literal(result.message()));
    }
}
