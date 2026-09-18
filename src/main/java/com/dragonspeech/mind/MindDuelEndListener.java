package com.dragonspeech.mind;

import net.minecraft.server.MinecraftServer;

/**
 * See MindDuelService.END_LISTENERS. Plain BiConsumer isn't enough here
 * because most real consumers (e.g. DragonHeartService, which needs to find
 * a ServerPlayer by UUID and edit an ItemStack in their inventory) need
 * the MinecraftServer reference too, which end() already has in scope.
 */
@FunctionalInterface
public interface MindDuelEndListener {
    void onDuelEnded(MinecraftServer server, ActiveMindDuel duel, DuelOutcome outcome);
}
