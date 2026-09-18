package com.dragonspeech.death;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where and when each player last died - the thread a resurrection
 * working reaches for. Entries expire: the longer the dead linger, the
 * further the thread frays, until it cannot be caught at all.
 * In-memory only; a server restart severs all threads, which is
 * acceptable lore ("the world moved on").
 */
public final class RecentDeaths {

    public record DeathRecord(UUID playerId, Vec3 position, ResourceKey<Level> dimension, long gameTime) {}

    /** How long the thread can still be caught, in ticks (90 seconds). */
    public static final long REACHABLE_TICKS = 20L * 90;

    /** A dead mob's remembered shape - enough to call it back whole. */
    public record MobDeathRecord(net.minecraft.world.entity.EntityType<?> type, net.minecraft.nbt.CompoundTag savedData,
                                 Vec3 position, ResourceKey<Level> dimension, long gameTime, float massFactor) {}

    private static final Map<UUID, DeathRecord> DEATHS = new HashMap<>();
    private static final java.util.ArrayDeque<MobDeathRecord> MOB_DEATHS = new java.util.ArrayDeque<>();
    private static final int MAX_MOB_DEATHS = 64;

    private RecentDeaths() {}

    public static void recordMob(MobDeathRecord record) {
        MOB_DEATHS.addFirst(record);
        while (MOB_DEATHS.size() > MAX_MOB_DEATHS) {
            MOB_DEATHS.removeLast();
        }
    }

    /** The most recent still-reachable mob death within `radius`, or null. */
    public static MobDeathRecord findMobNear(Vec3 point, ResourceKey<Level> dimension, double radius, long nowGameTime) {
        for (MobDeathRecord death : MOB_DEATHS) {
            if (!death.dimension().equals(dimension)) continue;
            if (nowGameTime - death.gameTime() > REACHABLE_TICKS) continue;
            if (death.position().distanceTo(point) > radius) continue;
            return death;
        }
        return null;
    }

    public static void clearMob(MobDeathRecord record) {
        MOB_DEATHS.remove(record);
    }

    public static void record(UUID playerId, Vec3 position, ResourceKey<Level> dimension, long gameTime) {
        DEATHS.put(playerId, new DeathRecord(playerId, position, dimension, gameTime));
    }

    public static void clear(UUID playerId) {
        DEATHS.remove(playerId);
    }

    /** The most recent still-reachable death within `radius` of the given point, or null. */
    public static DeathRecord findNear(Vec3 point, ResourceKey<Level> dimension, double radius, long nowGameTime) {
        DeathRecord best = null;
        for (DeathRecord death : DEATHS.values()) {
            if (!death.dimension().equals(dimension)) {
                continue;
            }
            if (nowGameTime - death.gameTime() > REACHABLE_TICKS) {
                continue;
            }
            if (death.position().distanceTo(point) > radius) {
                continue;
            }
            if (best == null || death.gameTime() > best.gameTime()) {
                best = death;
            }
        }
        return best;
    }
}
