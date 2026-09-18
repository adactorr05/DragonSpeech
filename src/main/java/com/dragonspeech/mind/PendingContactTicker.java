package com.dragonspeech.mind;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Checks in-flight Contact attempts every tick (fine granularity matters
 * here - a fully-mastered/hastened reach can be as short as half a
 * second, unlike the once-per-second pulse everything else in this
 * system uses) and resolves any whose travel time has elapsed via
 * ContactResolver.resolveReady().
 */
public final class PendingContactTicker {

    private PendingContactTicker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PendingContactTicker::tick);
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (PendingContact contact : PendingContactManager.allPending()) {
            if (contact.isReady(now)) {
                ContactResolver.resolveReady(server, contact);
            }
        }
    }
}
