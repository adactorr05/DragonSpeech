package com.dragonspeech.network;

import com.dragonspeech.guess.GuessOutcome;
import com.dragonspeech.guess.GuessResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GuessRequestHandler {

    private GuessRequestHandler() {}

    public static void handle(ServerPlayer player, String candidate) {
        GuessOutcome outcome = GuessResolver.resolve(player, candidate);
        player.sendSystemMessage(Component.literal(outcome.message()));
    }
}
