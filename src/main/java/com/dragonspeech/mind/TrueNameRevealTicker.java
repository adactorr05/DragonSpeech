package com.dragonspeech.mind;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the "/dragonspeech revealtruename" admin command - temporarily
 * shows an entity's true name as a real vanilla nametag floating above
 * its head (its actual CustomName, made visible), then reverts to
 * whatever name/visibility it had before, once the reveal duration ends.
 */
public final class TrueNameRevealTicker {

    private static final long REVEAL_DURATION_TICKS = 20L * 10; // 10 seconds

    private record Reveal(UUID entityId, Component originalName, boolean originalVisible, long revertAtGameTime) {}

    private static final Map<UUID, Reveal> ACTIVE = new HashMap<>();

    private TrueNameRevealTicker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TrueNameRevealTicker::tick);
    }

    /** Starts (or restarts) a reveal on this entity - true name shown as a nametag for REVEAL_DURATION_TICKS, then reverted. */
    public static void reveal(MinecraftServer server, Entity entity, String trueNamePlaintext) {
        UUID id = entity.getUUID();
        if (!ACTIVE.containsKey(id)) {
            ACTIVE.put(id, new Reveal(id, entity.getCustomName(), entity.isCustomNameVisible(),
                server.overworld().getGameTime() + REVEAL_DURATION_TICKS));
        } else {
            // Already mid-reveal (e.g. command run twice) - keep the ORIGINAL name on record, just extend the timer.
            Reveal existing = ACTIVE.get(id);
            ACTIVE.put(id, new Reveal(id, existing.originalName(), existing.originalVisible(),
                server.overworld().getGameTime() + REVEAL_DURATION_TICKS));
        }
        entity.setCustomName(Component.literal(trueNamePlaintext));
        entity.setCustomNameVisible(true);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Reveal>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Reveal reveal = iterator.next().getValue();
            if (now < reveal.revertAtGameTime()) {
                continue;
            }
            Entity entity = EntityLookup.byUUID(server, reveal.entityId());
            if (entity != null) {
                entity.setCustomName(reveal.originalName());
                entity.setCustomNameVisible(reveal.originalVisible());
            }
            iterator.remove();
        }
    }
}
