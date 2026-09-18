package com.dragonspeech.network;

import com.dragonspeech.scar.ScarAccess;
import com.google.gson.JsonArray;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Pushes a player's own scar descriptions to their client - on login and whenever ScarService adds one. Pre-described server-side since the server already has word names the client might not (a lost word, a cursed word). */
public final class ScarSyncHooks {

    private ScarSyncHooks() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> pushSync(handler.getPlayer()));
    }

    public static void pushSync(ServerPlayer player) {
        JsonArray array = new JsonArray();
        for (var scar : ScarAccess.get(player).scars()) {
            array.add(scar.describe());
        }
        DragonSpeechNetworking.sendScarSync(player, array.toString());
    }
}
