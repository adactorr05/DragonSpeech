package com.dragonspeech.mob.casting;

import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Thin coordination layer between MobSpellComposer (decide what to say) and
 * MobEffectExecutor (actually do it), plus the energy bookkeeping that ties
 * the two together. The mob-world equivalent of com.dragonspeech.cast.
 * CastExecutor - see MobSpellComposer's class comment for why the player
 * and mob pipelines are intentionally separate rather than sharing one.
 */
public final class MobCastExecutor {

    private MobCastExecutor() {}

    public static boolean tryCast(SpellcastingMob caster, SpellIntent intent, LivingEntity target) {
        return tryCast(caster, intent, target, caster.mysticalEnergy());
    }

    /** energyBudget: see MobSpellComposer's matching overload - how much of the caster's current energy it's willing to spend on this cast (may be less than everything it has, per MobCastStrategy's reserve). */
    public static boolean tryCast(SpellcastingMob caster, SpellIntent intent, LivingEntity target, float energyBudget) {
        if (com.dragonspeech.wow.WordOfWordsHaltManager.isCastingSuppressed(caster.asEntity())) {
            return false;
        }
        Optional<MobSpellComposer.ComposedSpell> composed = MobSpellComposer.compose(caster, intent, target, energyBudget);
        if (composed.isEmpty()) {
            return false;
        }
        MobSpellComposer.ComposedSpell spell = composed.get();
        float globalMultiplier = 1f;
        if (caster.asEntity().level() instanceof net.minecraft.server.level.ServerLevel level) {
            globalMultiplier = com.dragonspeech.wow.WordOfWordsRules.magicCostMultiplier(level.getServer());
        }
        float actualCost = spell.cost() * globalMultiplier;
        if (caster.mysticalEnergy() + 0.0001f < actualCost) {
            return false;
        }
        caster.setMysticalEnergy(caster.mysticalEnergy() - actualCost);

        // See WardLearner: records WHICH verb is about to be cast so
        // MobWards.applyWards can attribute a blocked hit to it, if this
        // caster is a ward-learning Shade. A no-op for anything else.
        if (caster instanceof WardLearner learner) {
            spell.composition().words().stream()
                .filter(w -> w.category() == WordCategory.VERB)
                .findFirst()
                .ifPresent(verb -> learner.setLastCastVerb(resourceLocationOf(verb)));
        }

        MobEffectExecutor.execute(spell.composition(), caster, target);
        NpcSpellDebug.announce(caster, spell.composition(), target); // no-op unless an admin has /dragonspeech debug npcspells true
        return true;
    }

    private static ResourceLocation resourceLocationOf(Word word) {
        return com.dragonspeech.DragonSpeech.id(word.trueName());
    }

    /** Tries each intent in order, spending freely (full current energy) - used where budgeting doesn't apply, e.g. the critical-health escape override in MobSpellCastGoal. */
    public static boolean tryCastBestOf(SpellcastingMob caster, List<SpellIntent> priorityOrder, LivingEntity target) {
        return tryCastBestOf(caster, priorityOrder, target, caster.mysticalEnergy());
    }

    /** Budget-aware version - see tryCast(..., energyBudget). */
    public static boolean tryCastBestOf(SpellcastingMob caster, List<SpellIntent> priorityOrder, LivingEntity target, float energyBudget) {
        for (SpellIntent intent : priorityOrder) {
            if (tryCast(caster, intent, target, energyBudget)) {
                return true;
            }
        }
        return false;
    }
}
