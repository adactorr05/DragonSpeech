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
 * Backs the Life domain's taint ladder (eitra / eitrbinda). Same
 * duration-scaling shape as FreezeEffectHandler/IgniteEffectHandler:
 * modifiers push duration within a hard-capped range, precision pushes
 * the amplifier (a crude eitra is a mild, lingering sickness; a bound
 * eitrbinda is a sharper, more concentrated poison), not the other way
 * around - so a caster can't out-word their way into an effect stronger
 * than this handler's own ceiling.
 */
public class PoisonEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        5, 10.0f, 20f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("poison");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 3.5f * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        float severitySeconds = 4f + (invocation.modifierMagnitudeSum() * 4f);
        severitySeconds = Math.max(1f, Math.min(severitySeconds, CAPS.maxMagnitudePerTarget()));
        int ticks = Math.round(severitySeconds * 20f);

        int amplifier = invocation.composition().averageMagnitudePrecision() >= 0.80f ? 1 : 0;

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amplifier));
                affected++;
            }
        }

        return EffectResult.success(affected, "The taint takes root.");
    }
}
