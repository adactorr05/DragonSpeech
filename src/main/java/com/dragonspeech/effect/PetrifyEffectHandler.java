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
 * Backs the Earth domain's petrify ladder (herda / steinherda /
 * steinbinda). HONESTY NOTE, not a compile caveat this time but a design
 * one: vanilla Minecraft has no "turn this entity to stone" primitive -
 * no model swap, no true immobilize. What this handler actually does is
 * stack Slowness (near max amplifier) with Mining Fatigue and Weakness,
 * which reads as "cannot move, cannot act with any force" but is a
 * gameplay approximation, not literal petrification. If your mod later
 * wants a REAL stone-skin visual (an actual model/texture change), that's
 * a separate rendering feature this handler cannot provide - flagging
 * that honestly rather than overselling what a potion-effect stack does.
 */
public class PetrifyEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        3, 8.0f, 20f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("petrify");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 5.0f * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        float severitySeconds = 3f + (invocation.modifierMagnitudeSum() * 3f);
        severitySeconds = Math.max(1f, Math.min(severitySeconds, CAPS.maxMagnitudePerTarget()));
        int ticks = Math.round(severitySeconds * 20f);

        // Precision decides how COMPLETE the stillness is (herda alone
        // leaves some Mining Fatigue slack; steinbinda locks it down
        // near-fully), rather than how long it lasts - duration stays
        // governed by modifiers alone, matching this project's other
        // ladders (Confuse, Poison).
        boolean fullyBound = invocation.composition().averageMagnitudePrecision() >= 0.85f;
        int slowAmplifier = fullyBound ? 6 : 3;
        int fatigueAmplifier = fullyBound ? 3 : 1;

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, slowAmplifier));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, fatigueAmplifier));
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
                affected++;
            }
        }

        return EffectResult.success(affected, "Flesh forgets it was ever anything but stone.");
    }
}
