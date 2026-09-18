package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.Element;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Backs "lyfta" - raises living targets off the ground with levitation. Modifiers push duration/strength within caps. */
public class LiftEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        4, 4.0f, 20f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("lift");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        // Lift is upward work by definition: mass, any downward velocity
        // being arrested (catching a fall costs in proportion to its
        // speed), and accumulated altitude all price in. See KineticCost.
        net.minecraft.world.phys.Vec3 up = new net.minecraft.world.phys.Vec3(0, 1, 0);
        float total = 0f;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(net.minecraft.world.entity.Entity entity)) {
                total += 5.0f * KineticCost.weightFor(entity, up);
            }
        }
        return Math.max(total, 5.0f);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        float seconds = 2f + (invocation.modifierMagnitudeSum() * 2f);
        seconds = Math.max(0.5f, Math.min(seconds, CAPS.maxMagnitudePerTarget()));
        int amplifier = invocation.modifierMagnitudeSum() > 0.3f ? 1 : 0;
        boolean usesWind = invocation.composition().words().stream()
            .anyMatch(w -> w.element().filter(e -> e == Element.WIND).isPresent());

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, Math.round(seconds * 20f), amplifier));
                if (usesWind && entity.level() instanceof ServerLevel level) {
                    Vec3 base = entity.position();
                    double height = Math.max(3.0, 4.5 + amplifier * 1.5 + seconds * .25);
                    SpellBodyVfx.emit(level, invocation.caster(), SpellBodyVfxType.UPDRAFT, List.of(Element.WIND),
                        base, base.add(0, height, 0), 1.0f + amplifier * .35f, 0f, Math.max(16, Math.round(seconds * 20f)));
                }
                affected++;
            }
        }

        return EffectResult.success(affected, "Weight forgets its claim, and the word bears them up.");
    }
}
