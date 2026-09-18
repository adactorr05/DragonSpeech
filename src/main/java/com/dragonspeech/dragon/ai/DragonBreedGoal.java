package com.dragonspeech.dragon.ai;

import com.dragonspeech.dragon.DragonEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.level.GameRules;

import java.util.List;

/**
 * Ported from Dragon Mounts Legacy
 * (com.github.kay9.dragonmounts.dragon.ai.DragonBreedGoal, GPL-3.0) -
 * one real change: the original posts a NeoForge-specific
 * BabyEntitySpawnEvent (a mod-compatibility hook for other Forge/
 * NeoForge mods to veto or modify breeding) before breeding actually
 * happens. That event has no Fabric equivalent and was removed rather
 * than faked - breeding now always proceeds when this goal fires,
 * without that cancellation hook.
 *
 * Functionally limited right now regardless: DragonEntity.
 * spawnChildFromBreeding() is currently a real no-op (see that file's
 * own note) pending a design decision on whether breeding even makes
 * sense once only egg-hatched dragons can be bonded - this goal will
 * fire, reset both dragons' love state, and increment their
 * reproduction counters, but won't actually spawn a child dragon until
 * that's resolved.
 */
public class DragonBreedGoal extends BreedGoal {
    private final DragonEntity dragon;

    public DragonBreedGoal(DragonEntity animal) {
        super(animal, 1);
        this.dragon = animal;
    }

    @Override
    public boolean canUse() {
        if (!dragon.isAdult()) {
            return false;
        }
        if (!dragon.isInLove()) {
            return false;
        } else {
            return (partner = getNearbyMate()) != null;
        }
    }

    public DragonEntity getNearbyMate() {
        List<DragonEntity> list = level.getEntitiesOfClass(DragonEntity.class, dragon.getBoundingBox().inflate(8d));
        double dist = Double.MAX_VALUE;
        DragonEntity closest = null;

        for (DragonEntity entity : list) {
            if (dragon.canMate(entity) && dragon.distanceToSqr(entity) < dist) {
                closest = entity;
                dist = dragon.distanceToSqr(entity);
            }
        }

        return closest;
    }

    @Override
    protected void breed() {
        animal.resetLove();
        partner.resetLove();
        dragon.spawnChildFromBreeding((ServerLevel) level, partner);
        level.broadcastEntityEvent(this.animal, (byte) 18);
        if (level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            level.addFreshEntity(new ExperienceOrb(level, animal.getX(), animal.getY(), animal.getZ(), animal.getRandom().nextInt(7) + 1));
        }
    }
}
