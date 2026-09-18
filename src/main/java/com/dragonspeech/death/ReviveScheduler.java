package com.dragonspeech.death;

import com.dragonspeech.effect.ResurrectEffectHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A short, deliberate delay (a few ticks) between "this player's revival
 * condition was met" and actually calling PlayerList.respawn(). Calling
 * respawn() in the SAME tick AFTER_DEATH fires risks racing vanilla's own
 * death bookkeeping (the client may not have finished transitioning to
 * the death screen yet, achievement/stat processing may still be mid-
 * flight, etc.) - a short queue drained on the ordinary server tick is
 * the standard, much more reliable pattern other mods use for "revive
 * this player automatically."
 */
public final class ReviveScheduler {

    private record Pending(UUID playerId, Vec3 position, ResourceKey<Level> dimension, int ticksLeft, String message) {}

    private static final List<Pending> QUEUE = new ArrayList<>();

    private ReviveScheduler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ReviveScheduler::tick);
    }

    public static void enqueue(ServerPlayer atDeath, String message) {
        QUEUE.add(new Pending(atDeath.getUUID(), atDeath.position(), atDeath.level().dimension(), 3, message));
    }

    private static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Pending> ready = new ArrayList<>();
        for (int i = QUEUE.size() - 1; i >= 0; i--) {
            Pending pending = QUEUE.get(i);
            if (pending.ticksLeft() <= 0) {
                ready.add(pending);
                QUEUE.remove(i);
            } else {
                QUEUE.set(i, new Pending(pending.playerId(), pending.position(), pending.dimension(), pending.ticksLeft() - 1, pending.message()));
            }
        }
        for (Pending pending : ready) {
            performRevive(server, pending);
        }
    }

    private static void performRevive(MinecraftServer server, Pending pending) {
        ServerPlayer departed = server.getPlayerList().getPlayer(pending.playerId());
        if (departed == null) {
            return; // disconnected before the delay elapsed - nothing to revive
        }

        ServerPlayer revived = departed;
        if (!departed.isAlive()) {
            revived = server.getPlayerList().respawn(departed, false, Entity.RemovalReason.KILLED);
        }

        if (revived.level() instanceof ServerLevel level && level.dimension().equals(pending.dimension())) {
            revived.teleportTo(level, pending.position().x, pending.position().y, pending.position().z,
                revived.getYRot(), revived.getXRot());
        }

        revived.setHealth(Math.max(1f, revived.getMaxHealth() / 2f));
        revived.getFoodData().setFoodLevel(6);

        ResurrectEffectHandler.applyScarPublic(revived);
        revived.sendSystemMessage(Component.literal(pending.message()));
    }
}
