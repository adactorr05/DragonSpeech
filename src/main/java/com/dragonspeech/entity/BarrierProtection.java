package com.dragonspeech.entity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Projectiles get stopped by MagicBarrierEntity's own bounding box for
 * free, through vanilla's normal projectile-vs-entity collision - no
 * code needed here for that. This is for everything that reaches a
 * defended target WITHOUT physically colliding with the barrier first:
 * melee swings, explosions, fire, and similar - anything whose damage
 * source doesn't travel as a real projectile through the world.
 */
public final class BarrierProtection {

    private BarrierProtection() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity.level() instanceof ServerLevel level)) {
                return true;
            }
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return true; // the void reaches through any shield, same as wards
            }

            AABB nearby = entity.getBoundingBox().inflate(4.0);
            for (MagicBarrierEntity barrier : level.getEntitiesOfClass(MagicBarrierEntity.class, nearby,
                b -> !b.isRemoved())) {
                if (barrier.protects(entity)) {
                    barrier.absorbDamage(amount, source);
                    return false;
                }
            }
            return true;
        });
    }
}
