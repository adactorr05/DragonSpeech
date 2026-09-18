package com.dragonspeech.mob.casting;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Keeps a spellcasting mob at a preferred casting range from its current
 * target instead of standing still or walking into melee - none of the
 * other goals registered on these mobs (MobSpellCastGoal only ever sets
 * Goal.Flag.LOOK; the wander/float goals only run while there's no
 * target) actually move a mob TOWARD or AWAY FROM a target at all, so
 * without this a caster with an active target would just stand in place
 * casting whatever it could afford and otherwise do nothing.
 *
 * Deliberately generic over SpellcastingMob (works for both PathfinderMob-
 * based mobs and Monster-based ShadeEntity, same "talk to the interface,
 * not a concrete class" pattern MobSpellCastGoal already uses) rather than
 * living on SpellcastingMobEntity - Shade needs it too.
 *
 * Uses Goal.Flag.MOVE only (not LOOK), so it runs concurrently alongside
 * MobSpellCastGoal at a different priority without either one interrupting
 * the other - see each concrete entity's registerGoals() for the actual
 * priority wiring.
 */
public class MobKeepDistanceGoal extends Goal {

    private final Mob mob;
    private final double preferredMin;
    private final double preferredMax;
    private final double speedModifier;

    private int repathCooldown;

    public MobKeepDistanceGoal(Mob mob, double preferredMin, double preferredMax, double speedModifier) {
        if (!(mob instanceof SpellcastingMob)) {
            throw new IllegalArgumentException("MobKeepDistanceGoal requires a SpellcastingMob, got " + mob.getClass());
        }
        this.mob = mob;
        this.preferredMin = preferredMin;
        this.preferredMax = preferredMax;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 10; // re-evaluate roughly twice a second, not every tick

        double distSq = mob.distanceToSqr(target);
        double minSq = preferredMin * preferredMin;
        double maxSq = preferredMax * preferredMax;

        if (distSq < minSq) {
            Vec3 away = mob.position().subtract(target.position());
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(mob.getRandom().nextDouble() - 0.5, 0, mob.getRandom().nextDouble() - 0.5);
            }
            away = away.normalize();
            Vec3 dest = mob.position().add(away.scale(preferredMin - Math.sqrt(distSq) + 2.0));
            mob.getNavigation().moveTo(dest.x, dest.y, dest.z, speedModifier);
        } else if (distSq > maxSq) {
            mob.getNavigation().moveTo(target, speedModifier);
        } else {
            mob.getNavigation().stop();
        }
    }
}
