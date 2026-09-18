package com.dragonspeech.mind;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary stamina interference caused by occupied-mind actions. This is
 * deliberately not a scar: scars are lasting crossing damage, while these
 * effects are short tactical pressure applied by the attacker after a breach.
 */
public final class MindStaminaInterference {

    private static final Map<UUID, Long> regenStoppedUntil = new HashMap<>();
    private static final Map<UUID, Long> regenSlowedUntil = new HashMap<>();

    private MindStaminaInterference() {}

    public static void stopRegen(ServerPlayer player, long untilGameTime) {
        regenStoppedUntil.put(player.getUUID(), untilGameTime);
    }

    public static void slowRegen(ServerPlayer player, long untilGameTime) {
        regenSlowedUntil.put(player.getUUID(), untilGameTime);
    }

    public static boolean regenStopped(ServerPlayer player, long gameTime) {
        return regenStoppedUntil.getOrDefault(player.getUUID(), 0L) > gameTime;
    }

    public static float regenMultiplier(ServerPlayer player, long gameTime) {
        return regenSlowedUntil.getOrDefault(player.getUUID(), 0L) > gameTime ? 0.35f : 1.0f;
    }

    public static void prune(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        pruneExpired(regenStoppedUntil, now);
        pruneExpired(regenSlowedUntil, now);
    }

    private static void pruneExpired(Map<UUID, Long> map, long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }
    }
}
