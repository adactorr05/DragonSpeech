package com.dragonspeech.mob.casting;

import com.dragonspeech.detection.DetectionService;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/**
 * "skynja afl"/"skynja naerum afl" to sense a target's stamina, "skynja
 * marka"/"skynja naerum marka" to sense whether it carries a mark - per
 * explicit direction. Deliberately its own small goal rather than folded
 * into MobSpellCastGoal/SpellIntent: these two specific word PAIRS are
 * hand-recognized directly (by true name) rather than routed through the
 * normal effect_handler-based intent system, since "skynja" is a
 * prerequisite/base word with no effect_handler of its own in the mob
 * vocabulary sense, and detection isn't really an "effect" the same way
 * push/shock/heal are - see DetectionService for where the actual
 * reveal happens.
 *
 * Flat, cheap energy cost (not run through SpellCostCalculator) since
 * this is a read-only sensing act, same "costs almost nothing" spirit
 * the real player-facing SkynjaEffectHandler already gives skynja.
 */
public class MobDetectionCastGoal extends Goal {

    private static final float ENERGY_COST = 8f;
    private static final ResourceLocation SKYNJA = com.dragonspeech.DragonSpeech.id("skynja");
    private static final ResourceLocation AFL = com.dragonspeech.DragonSpeech.id("afl");
    private static final ResourceLocation MARKA = com.dragonspeech.DragonSpeech.id("marka");

    private final Mob mob;
    private final SpellcastingMob caster;
    private int cooldown;

    public MobDetectionCastGoal(Mob mob) {
        if (!(mob instanceof SpellcastingMob spellcastingMob)) {
            throw new IllegalArgumentException("MobDetectionCastGoal requires a SpellcastingMob, got " + mob.getClass());
        }
        this.mob = mob;
        this.caster = spellcastingMob;
        this.cooldown = mob.getRandom().nextInt(100);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (caster.mysticalEnergy() < ENERGY_COST) {
            return false;
        }
        List<ResourceLocation> known = List.copyOf(caster.vocabulary().words());
        return (known.contains(SKYNJA) && known.contains(AFL)) || (known.contains(SKYNJA) && known.contains(MARKA));
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        List<ResourceLocation> known = List.copyOf(caster.vocabulary().words());
        boolean canStamina = known.contains(SKYNJA) && known.contains(AFL);
        boolean canMark = known.contains(SKYNJA) && known.contains(MARKA);

        boolean staminaFirst = canStamina && (!canMark || mob.getRandom().nextBoolean());
        float magnitudeBonus = magnitudeBonus(known);

        if (staminaFirst) {
            DetectionService.revealStamina(level, target, magnitudeBonus);
        } else if (canMark) {
            DetectionService.revealMark(level, target, magnitudeBonus);
        } else {
            return;
        }

        caster.setMysticalEnergy(caster.mysticalEnergy() - ENERGY_COST);
        cooldown = 300 + mob.getRandom().nextInt(200);
    }

    /** Any known positive-magnitude modifier (mikla, ofsa, tvefalt) extends the reveal duration - "unless they use mikla or some other modifier" per direction. */
    private float magnitudeBonus(List<ResourceLocation> known) {
        float best = 0f;
        for (ResourceLocation id : known) {
            Word word = WordRegistry.get(id);
            if (word != null && word.isModifierWord() && word.modifierMagnitude() > best) {
                best = word.modifierMagnitude();
            }
        }
        return best;
    }
}
