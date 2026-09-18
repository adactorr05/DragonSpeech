package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;

/**
 * Backs the Mind domain's Illusion/Deception ladder (villa / blekkja /
 * gervimynd): unlike hugsnert/hugleita/hugvarna/hugrista/hugbinda (which
 * are trained SKILLS gating the mind-duel system via SkillHooks, never
 * castable in the moment), these three are ordinary VERBs like any
 * fire/water/force verb - they resolve through this handler the instant
 * they're cast, no duel required. This is the fix for the earlier
 * "Mind Trick" mistranslation: deceiving/confusing a mind is a cast,
 * not a passive sense (hugsnert has no effect_handler at all, and
 * should not gain one - it stays a pure sense/skill word).
 *
 * NOTE: this project's other handlers use older Mojang-mapping constant
 * names throughout (e.g. FreezeEffectHandler's MOVEMENT_SLOWDOWN rather
 * than SLOWNESS), so CONFUSION/BLINDNESS are used here to match that
 * existing convention exactly. As with ContactBeamRenderer elsewhere in
 * this pass, there is no compiler available in this environment to
 * verify these against your exact mapped version - if either constant
 * name has moved in your MC version, it's a one-line fix in this file.
 */
public class ConfuseEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        4, 8.0f, 20f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("confuse");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 3.0f * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        // Same shape as FreezeEffectHandler's severity math: modifier
        // words (mikla/litla/ofsa/...) push duration up or down, clamped
        // to this handler's own hard cap regardless of wording.
        float severitySeconds = 3f + (invocation.modifierMagnitudeSum() * 3f);
        severitySeconds = Math.max(1f, Math.min(severitySeconds, CAPS.maxMagnitudePerTarget()));
        int ticks = Math.round(severitySeconds * 20f);

        // Higher-precision words in the sentence (blekkja/gervimynd vs.
        // crude villa) should read as a MORE convincing falsehood, not a
        // stronger stun - so precision nudges the Blindness amplifier
        // (how thoroughly the target's own senses lie to it) rather than
        // duration, which stays governed by modifiers alone.
        int amplifier = invocation.composition().averageMagnitudePrecision() >= 0.85f ? 1 : 0;

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0));
                living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, amplifier));
                affected++;
            }
        }

        return EffectResult.success(affected, "The mind is shown what is not there.");
    }
}
