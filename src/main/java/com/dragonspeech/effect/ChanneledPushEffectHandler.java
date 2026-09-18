package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * A held, continuous version of push - a gentle steady nudge applied
 * repeatedly for as long as the caster keeps the word open, rather than
 * one strong shove. Backs "haldthrysta" (hold-force), gated behind
 * already knowing "thrysta" (the instant push). See ChannelManager for
 * how starting/stopping a channel works.
 */
public class ChanneledPushEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        3, 1.0f, 16f, Set.of(TargetKind.ENTITY)
    );

    private static final float COST_PER_TICK = 1.5f;

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("channel_push");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public boolean isChanneled() {
        return true;
    }

    @Override
    public float costPerTick(EffectInvocation invocation) {
        // Evaluated FRESH each pulse (see ChannelManager): as the held
        // target climbs, falls faster, or the direction fights harder,
        // this pulse's price reflects it - holding something aloft gets
        // steadily more expensive the higher you take it.
        Vec3 spoken = KineticDirections.combined(invocation);
        Vec3 casterPos = invocation.caster().position();
        float total = 0f;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity)) {
                Vec3 intent = spoken != null ? spoken
                    : entity.position().subtract(casterPos).normalize();
                total += 1.5f * KineticCost.weightFor(entity, intent);
            }
        }
        return Math.max(total, 1.5f);
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        // Only relevant if something wants a one-shot cost preview - the
        // channel itself is priced through costPerTick, not this.
        return COST_PER_TICK * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        double strength = 0.15 + (invocation.modifierMagnitudeSum() * 0.1);
        strength = Math.max(0.02, Math.min(strength, CAPS.maxMagnitudePerTarget()));

        Vec3 spoken = KineticDirections.combined(invocation);
        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity)) {
                Vec3 direction;
                double lift;
                if (spoken != null) {
                    direction = spoken;
                    lift = spoken.y * strength;
                } else {
                    Vec3 away = entity.position().subtract(invocation.caster().position());
                    direction = away.lengthSqr() > 0.0001 ? away.normalize() : new Vec3(0, 0, 1);
                    lift = 0.05;
                }
                entity.push(direction.x * strength, lift, direction.z * strength);
                entity.hurtMarked = true;
                affected++;
            }
        }

        return EffectResult.success(affected, "The word holds them fast, pressing steadily.");
    }
}
