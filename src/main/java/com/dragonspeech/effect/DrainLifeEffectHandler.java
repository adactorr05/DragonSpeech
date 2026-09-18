package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Backs "lifssuga" - tears life-force out of a living target as real
 * damage. "lifflyta" (paired modifier word) converts a portion of the
 * damage ACTUALLY dealt (post-armor, post-resistance - measured by the
 * target's real health drop, not the raw amount requested) into healing
 * for the caster. Without lifflyta this is plain damage; a target's
 * undead-inverted healing (isInvertedHealAndHarm) is respected the same
 * way Element.LIFE already handles it elsewhere in this mod.
 */
public class DrainLifeEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 10.0f, 16f, Set.of(TargetKind.ENTITY)
    );

    /** How much of the damage actually dealt comes back as healing when lifflyta is spoken - a real cost to greed, not a 1:1 free trade. */
    private static final float LIFESTEAL_FRACTION = 0.65f;

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("drain_life");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 6f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        var capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }
        if (invocation.targets().isEmpty()
            || !(invocation.targets().get(0) instanceof EffectTarget.OfEntity(Entity raw))
            || !(raw instanceof LivingEntity target)) {
            return EffectResult.failure("There is no life there to draw out.");
        }
        if (target == caster) {
            return EffectResult.failure("You cannot tear your own life from yourself this way.");
        }

        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst().map(Word::precision).orElse(0.5f);
        float power = 3f + verbPrecision * 4f + invocation.modifierMagnitudeSum() * 3f;
        power = Math.max(1f, Math.min(power, CAPS.maxMagnitudePerTarget()));

        float requestedDamage = power * 0.7f;
        float healthBefore = target.getHealth();
        target.hurt(level.damageSources().indirectMagic(caster, caster), requestedDamage);
        float actuallyDealt = Math.max(0f, healthBefore - target.getHealth());

        boolean flyta = invocation.composition().words().stream().anyMatch(w -> "lifflyta".equals(w.trueName()));

        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        if (flyta && actuallyDealt > 0f) {
            float healAmount = actuallyDealt * LIFESTEAL_FRACTION;
            if (caster.isInvertedHealAndHarm()) {
                caster.hurt(level.damageSources().magic(), healAmount);
            } else {
                caster.heal(healAmount);
            }
            Vec3 casterPos = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
            SpellFx.trail(level, DragonSpeechParticles.DARK_MAGIC, 0x6b1030, 0x0e0413, targetPos, casterPos, 0.4);
            SpellFx.spiral(level, DragonSpeechParticles.SPARKLE, 0x8bff8b, 0xd8ffd8, caster, 0.6);
        }
        SpellFx.burst(level, DragonSpeechParticles.DARK_MAGIC, 0x6b1030, 0x0e0413, targetPos, 10, 0.1);

        return EffectResult.success(1, flyta
            ? "Their life tears free and rushes into you."
            : "Their life tears free and scatters, spent for nothing.");
    }
}
