package com.dragonspeech.mob.casting;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * "should be able to place wards on themselves even during a fight if
 * they want" per explicit direction - slowly re-adds any known-and-
 * eligible ward type that isn't currently active (either never rolled,
 * or depleted from absorbing hits - see MobWards.applyWards). Runs
 * whether idle or mid-fight: no target required, and Goal.Flag is empty
 * (no LOOK/MOVE) so it never competes with or interrupts combat goals -
 * it just quietly re-raises a ward back to full durability in the
 * background every so often.
 */
public class MobSelfWardGoal extends Goal {

    private final Mob mob;
    private final Warded warded;
    private final MobPowerTier tier;
    private final List<MobWards.WardType> knownWardTypes;
    private int cooldown;

    public MobSelfWardGoal(Mob mob, Warded warded, MobPowerTier tier, List<MobWards.WardType> knownWardTypes) {
        this.mob = mob;
        this.warded = warded;
        this.tier = tier;
        this.knownWardTypes = knownWardTypes;
        this.cooldown = mob.getRandom().nextInt(200);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        Map<MobWards.WardType, MobWards.WardInstance> active = warded.activeWards();
        return knownWardTypes.stream().anyMatch(type -> !active.containsKey(type));
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        Map<MobWards.WardType, MobWards.WardInstance> active = warded.activeWards();
        knownWardTypes.stream()
            .filter(type -> !active.containsKey(type))
            .findFirst()
            .ifPresent(type -> active.put(type, new MobWards.WardInstance(type, MobWards.durabilityFor(tier))));
        cooldown = 400 + mob.getRandom().nextInt(400);
    }
}
