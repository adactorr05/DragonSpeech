package com.dragonspeech.stamina;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies genuinely lethal damage from DrainResolver.applyLethalDrain()
 * one tick late, on purpose. That method is reached from
 * LivingEntityDamageMixin while a LivingEntity.hurt() call is already
 * mid-flight (the ward absorption path) - calling hurt() again from
 * inside that same call stack is the kind of reentrancy that corrupts
 * invulnerability ticks and damage-source bookkeeping in subtle,
 * hard-to-reproduce ways. Queuing it here and firing it on the next
 * END_SERVER_TICK sidesteps that entirely: by the time it runs, the
 * original hurt() call has long since returned.
 *
 * In-memory only, like every other short-lived tick queue in this mod
 * (SigilManager, StasisManager) - a server restart between the queue and
 * the next tick would just drop it, which is the right failure mode for
 * something this transient.
 */
public final class LethalDrainQueue {

    private record Pending(UUID playerId, float healthDamage) {}

    private static final List<Pending> QUEUE = new ArrayList<>();

    private LethalDrainQueue() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LethalDrainQueue::tick);
    }

    static void queue(ServerPlayer player, float healthDamage) {
        if (healthDamage > 0f) {
            QUEUE.add(new Pending(player.getUUID(), healthDamage));
        }
    }

    private static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Pending> batch = List.copyOf(QUEUE);
        QUEUE.clear();

        for (Pending pending : batch) {
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            if (player != null && player.isAlive()) {
                player.hurt(player.damageSources().magic(), pending.healthDamage());
            }
        }
    }
}
