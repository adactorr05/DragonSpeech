package com.dragonspeech.wow;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Temporary Word-of-Words prohibitions. Never persisted across restart. */
public final class WordOfWordsHaltManager {
    private record Zone(long id, ResourceKey<Level> dimension, Vec3 center, double radius, long expiresAt) {}
    private record EntityHalt(long id, UUID entityId, ResourceKey<Level> dimension, long expiresAt) {}

    /** Read-only information exposed to the Word-of-Words GUI. */
    public record ZoneView(long id, Vec3 center, double radius, long remainingTicks) {}
    public record EntityHaltView(long id, UUID entityId, long remainingTicks) {}

    private static final List<Zone> ZONES = new ArrayList<>();
    private static final List<EntityHalt> ENTITIES = new ArrayList<>();
    private static final AtomicLong IDS = new AtomicLong(1);

    private WordOfWordsHaltManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(WordOfWordsHaltManager::tick);
    }

    public static long suppressArea(ServerLevel level, Vec3 center, double radius, int ticks) {
        long id = IDS.getAndIncrement();
        ZONES.add(new Zone(id, level.dimension(), center, radius, level.getServer().getTickCount() + ticks));
        return id;
    }

    /** Works for players AND NPC spellcasters (Shade/Elf/Elder Elf/Human Mage/etc.). */
    public static long suppressEntity(LivingEntity entity, int ticks) {
        if (!(entity.level() instanceof ServerLevel level)) return -1;
        ENTITIES.removeIf(p -> p.entityId().equals(entity.getUUID()));
        long id = IDS.getAndIncrement();
        ENTITIES.add(new EntityHalt(id, entity.getUUID(), level.dimension(), level.getServer().getTickCount() + ticks));
        return id;
    }

    public static void suppressPlayer(ServerPlayer player, int ticks) {
        suppressEntity(player, ticks);
    }

    public static boolean isCastingSuppressed(ServerPlayer player) {
        return isCastingSuppressed((LivingEntity) player);
    }

    /** Shared gate for every Dragon Speech caster, not just players. */
    public static boolean isCastingSuppressed(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        long now = server.getTickCount();
        for (EntityHalt halt : ENTITIES) {
            if (halt.expiresAt() > now && halt.dimension().equals(level.dimension()) && halt.entityId().equals(entity.getUUID())) return true;
        }
        for (Zone zone : ZONES) {
            if (zone.expiresAt() <= now || !zone.dimension().equals(level.dimension())) continue;
            if (entity.position().distanceToSqr(zone.center()) <= zone.radius() * zone.radius()) return true;
        }
        return false;
    }

    /** The selected entity-specific prohibition, if one is currently active. */
    public static EntityHaltView haltFor(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        long now = level.getServer().getTickCount();
        for (EntityHalt halt : ENTITIES) {
            if (halt.expiresAt() > now && halt.dimension().equals(level.dimension()) && halt.entityId().equals(entity.getUUID())) {
                return new EntityHaltView(halt.id(), halt.entityId(), halt.expiresAt() - now);
            }
        }
        return null;
    }

    /** Word-created suppression fields close enough to the current focus to be individually removed. */
    public static List<ZoneView> nearbyZones(ServerLevel level, Vec3 focus, double scanRadius) {
        long now = level.getServer().getTickCount();
        double r2 = scanRadius * scanRadius;
        ArrayList<ZoneView> out = new ArrayList<>();
        for (Zone zone : ZONES) {
            if (zone.expiresAt() <= now || !zone.dimension().equals(level.dimension())) continue;
            double allowance = scanRadius + zone.radius();
            if (zone.center().distanceToSqr(focus) > allowance * allowance && zone.center().distanceToSqr(focus) > r2) continue;
            out.add(new ZoneView(zone.id(), zone.center(), zone.radius(), zone.expiresAt() - now));
        }
        out.sort(Comparator.comparingDouble(v -> v.center().distanceToSqr(focus)));
        return List.copyOf(out);
    }

    public static boolean removeZone(long id) {
        return ZONES.removeIf(z -> z.id() == id);
    }

    public static boolean removeEntityHalt(long id) {
        return ENTITIES.removeIf(h -> h.id() == id);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        ZONES.removeIf(z -> z.expiresAt() <= now);
        ENTITIES.removeIf(p -> p.expiresAt() <= now);
    }
}
