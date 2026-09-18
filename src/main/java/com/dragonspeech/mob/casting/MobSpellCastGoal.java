package com.dragonspeech.mob.casting;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Generic "try to cast something at my current target" AI goal - works for
 * any SpellcastingMob regardless of which vanilla base class it extends
 * (PathfinderMob or Monster), since it only ever talks to the
 * SpellcastingMob interface, never a concrete entity class.
 *
 * Tries each SpellIntent in priorityOrder in turn and casts the first one
 * MobSpellComposer can actually afford; a mob that knows nothing useful for
 * any of them, or can't currently afford any of them, simply does nothing
 * this "turn" (still pays the cooldown, so it isn't re-evaluating every
 * single tick) and falls back to its other goals - melee, fleeing,
 * wandering - for that tick. This is intentionally a thin, generic
 * scheduler; per-race target priorities (e.g. an Elder Elf preferring to
 * shield an ally before it attacks) belong in each concrete entity's own
 * priorityOrder list, not in this class.
 *
 * maxCastRange (NEW - fixes "Shades attacking from extremely long
 * distance"): nothing previously gated casting by distance at all, so a
 * mob with a large FOLLOW_RANGE (Shade's is 48) could blast a target it
 * had line of sight to from clear across the map. Each entity class now
 * passes a value matching its own MobKeepDistanceGoal's max preferred
 * range plus a small buffer, so a mob simply won't attempt to cast
 * further than it's actually trying to close to anyway.
 *
 * One exception to "just walk the list in order": below ESCAPE_HEALTH_
 * FRACTION health, a mob that has SpellIntent.MOBILITY anywhere in its
 * priority list will always try that FIRST, out of list order - a
 * desperate blink/pillar-launch away takes priority over finishing a
 * fight it's clearly losing. Mobs that don't list MOBILITY at all (a
 * plain Elf, say) simply never get this behavior; there's nothing to
 * special-case for them.
 */
public class MobSpellCastGoal extends Goal {

    private static final float ESCAPE_HEALTH_FRACTION = 0.25f;

    private final Mob mob;
    private final SpellcastingMob caster;
    private final List<SpellIntent> priorityOrder;
    private final double maxCastRangeSq;

    public MobSpellCastGoal(Mob mob, List<SpellIntent> priorityOrder, double maxCastRange) {
        if (!(mob instanceof SpellcastingMob spellcastingMob)) {
            throw new IllegalArgumentException("MobSpellCastGoal requires a SpellcastingMob, got " + mob.getClass());
        }
        this.mob = mob;
        this.caster = spellcastingMob;
        this.priorityOrder = priorityOrder;
        this.maxCastRangeSq = maxCastRange * maxCastRange;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && caster.canCastNow()
            && mob.distanceToSqr(target) <= maxCastRangeSq;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target);

        LivingEntity self = caster.asEntity();
        boolean critical = self.getHealth() <= self.getMaxHealth() * ESCAPE_HEALTH_FRACTION;
        if (critical && priorityOrder.contains(SpellIntent.MOBILITY)
            && MobCastExecutor.tryCast(caster, SpellIntent.MOBILITY, target)) {
            caster.markCastUsed();
            return;
        }

        // Addon extension point (see com.dragonspeech.api.EntityBehaviorBrain's own doc) - when a
        // brain is registered AND accepts this entity, its decision is used INSTEAD of the
        // shuffle+strategy logic below for this cast attempt. PASS (or no brain registered at all)
        // falls straight through to that same built-in heuristic, unchanged.
        var behaviorBrain = com.dragonspeech.api.EntityBehaviorBrainRegistry.get();
        if (behaviorBrain != null && behaviorBrain.handles(self)) {
            var context = new com.dragonspeech.api.EntityBehaviorBrain.EntityBehaviorSpellContext(
                    caster, target, priorityOrder, caster.mysticalEnergy());
            var decision = behaviorBrain.decideSpell(context);
            if (!decision.isPass()) {
                if (decision.intent() != null) {
                    MobCastExecutor.tryCast(caster, decision.intent(), target);
                }
                // Whether the cast actually succeeded or not, the brain made a real decision for
                // this attempt - still consumes the cooldown like the built-in heuristic does, so a
                // brain that keeps trying something it can't afford doesn't re-evaluate every tick.
                caster.markCastUsed();
                return;
            }
        }

        // FIX: "instead of using a variety of spells, they continue to
        // use the same spell" (part 2 - see MobSpellComposer's matching
        // fix for part 1). priorityOrder used to be walked in the exact
        // same fixed order every cast, so whichever intent came first in
        // the list (CROWD_CONTROL for most of these mobs) would win
        // almost every time it had anything affordable at all, starving
        // the others. Shuffling a working copy per cast lets every
        // listed intent get a turn leading, not just the first one.
        //
        // Collections.shuffle only accepts java.util.Random -
        // RandomSource (what Entity.getRandom() returns) doesn't
        // implement it, confirmed by a real compile error. Fisher-Yates
        // by hand instead.
        List<SpellIntent> shuffled = new ArrayList<>(priorityOrder);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = self.getRandom().nextInt(i + 1);
            SpellIntent tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }

        // STAMINA MANAGEMENT: "manage between defense, offense, and
        // their healing/wards/utility... more spells faster if
        // pressured, steady management otherwise" per explicit
        // direction. Before this, a mob spent whatever it had the
        // instant it was affordable, every cooldown cycle, with no
        // concept of holding anything back - see MobCastStrategy for
        // the fuller reasoning. DEFENSIVE additionally moves
        // SELF_HEAL/CROWD_CONTROL ahead of OFFENSE (still shuffled
        // within those two groups, so it isn't a second fixed order).
        MobCastStrategy strategy = MobCastStrategy.choose(caster);
        float maxEnergy = caster.maxMysticalEnergy();
        float reserve = maxEnergy * strategy.reserveFraction();
        float budget = Math.max(0f, caster.mysticalEnergy() - reserve);

        List<SpellIntent> ordered = shuffled;
        if (strategy.preferDefenseFirst()) {
            List<SpellIntent> defenseFirst = new ArrayList<>();
            List<SpellIntent> rest = new ArrayList<>();
            for (SpellIntent intent : shuffled) {
                if (intent == SpellIntent.SELF_HEAL || intent == SpellIntent.CROWD_CONTROL) {
                    defenseFirst.add(intent);
                } else {
                    rest.add(intent);
                }
            }
            ordered = new ArrayList<>(defenseFirst.size() + rest.size());
            ordered.addAll(defenseFirst);
            ordered.addAll(rest);
        }

        MobCastExecutor.tryCastBestOf(caster, ordered, target, budget);
        caster.markCastUsed();
    }
}
