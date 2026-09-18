package com.dragonspeech.api;

import com.dragonspeech.mob.casting.SpellIntent;
import com.dragonspeech.mob.casting.SpellcastingMob;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * BASE MOD extension point for Entity Behavior AI - the counterpart to
 * MindDuelBrain, for a DIFFERENT axis (which spell to cast, not mind-duel
 * bar-clicking). Per the Neural Network addon's own design doc, section 9:
 * a registrable "brain" interface checked first, falling back to the
 * built-in heuristic (MobSpellCastGoal's own shuffled-priority + stamina-
 * strategy logic) if none is registered or the brain passes.
 *
 * SCOPE OF THIS PASS: spellcasting intent selection only (the actual
 * "which SpellIntent to try" decision inside MobSpellCastGoal#start()).
 * The design doc's Entity Behavior mode also covers movement (Move Toward/
 * Flee/Reposition), wards, and ally coordination (Broadcast/Listen) - those
 * are separate Goal classes (MobKeepDistanceGoal, MobSelfWardGoal) that
 * don't have a brain hook yet. Spellcasting was scoped first since it's
 * the concretely-requested use case ("use spells... based off the words
 * they know") - the same "check first, fall back to heuristic" pattern
 * extends to those other goals the same way, whenever that's built.
 *
 * SERVER-SIDE ONLY - MobSpellCastGoal only ever runs from server-side
 * entity AI ticking, same reasoning as MindDuelBrain's own doc.
 *
 * ONE BRAIN AT A TIME (see EntityBehaviorBrainRegistry) - same reasoning
 * as MindDuelBrainRegistry: a single registration slot is the smaller,
 * more stable surface for the one addon this exists for today.
 */
public interface EntityBehaviorBrain {

    /** Checked once per cast attempt, before decideSpell() - lets a brain opt in per-entity (e.g. only entities with a saved network configured). */
    boolean handles(LivingEntity entity);

    /**
     * Called at the exact point MobSpellCastGoal#start() would otherwise
     * shuffle its own priority list and apply MobCastStrategy - see that
     * class's own doc for what it does when no brain is registered/handling.
     * Return {@link EntityBehaviorSpellDecision#PASS} to defer to that
     * built-in logic for this cast attempt.
     */
    EntityBehaviorSpellDecision decideSpell(EntityBehaviorSpellContext context);

    /** Read-only snapshot of what MobSpellCastGoal already knows at the point it would decide. */
    record EntityBehaviorSpellContext(
            SpellcastingMob caster,
            LivingEntity target,
            List<SpellIntent> availableIntents,
            float energyBudget
    ) {
    }

    /** What the brain decided - cast this specific intent, or pass (defer to the built-in heuristic for this attempt). */
    record EntityBehaviorSpellDecision(SpellIntent intent, boolean isPass) {
        public static final EntityBehaviorSpellDecision PASS = new EntityBehaviorSpellDecision(null, true);

        public static EntityBehaviorSpellDecision cast(SpellIntent intent) {
            return new EntityBehaviorSpellDecision(intent, false);
        }
    }
}
