package com.dragonspeech.mind;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Turns the two raw C2S payloads (ReachOutPayload, MindDuelActionPayload)
 * into calls against ContactResolver / MindDuelActionService. Both
 * DragonSpeechNetworking receivers hop back onto the server thread before
 * calling into here, same as every other network handler in this mod.
 */
public final class MindDuelRequestHandler {

    private MindDuelRequestHandler() {}

    public static void handleReachOut(ServerPlayer player, int targetEntityId) {
        Entity target = player.level().getEntity(targetEntityId);
        if (!(target instanceof LivingEntity livingTarget) || target == player) {
            player.sendSystemMessage(Component.literal("There is nothing there to reach."));
            return;
        }
        ContactResolver.ContactOutcome outcome = ContactResolver.attempt(player, livingTarget);
        // ContactResolver sends its own richer flavor text for the roll-based
        // success/fail cases; this catches the early-gate cases (no skill,
        // on cooldown, already in a duel) that return before messaging
        // anyone themselves. A short duplicate line in the roll-based case
        // is a harmless tradeoff for not silently swallowing the gate cases.
        if (!outcome.success()) {
            player.sendSystemMessage(Component.literal(outcome.message()));
        }
    }

    public static void handleAction(ServerPlayer player, String rawAction, String param) {
        ActiveMindDuel duel = MindDuelManager.forParticipant(player.getUUID()).orElse(null);
        if (duel == null) {
            return; // stale click from a screen the server already closed - not an error
        }

        DuelAction action;
        try {
            action = DuelAction.valueOf(rawAction.toUpperCase());
        } catch (IllegalArgumentException e) {
            return; // malformed/stale client payload - ignore rather than crash the handler
        }

        DuelActionResult result = MindDuelActionService.resolve(player.getServer(), duel, player.getUUID(), action, param);
        player.sendSystemMessage(Component.literal(result.message()));
    }
}
