package com.dragonspeech.engine;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.effect.EffectHandler;
import com.dragonspeech.effect.EffectHandlerCaps;
import com.dragonspeech.effect.EffectInvocation;
import com.dragonspeech.effect.EffectResult;
import com.dragonspeech.effect.EffectTarget;
import com.dragonspeech.effect.TargetKind;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * The time-binding (tidbinda / kyrra): one handler, three outcomes, all
 * decided by the sentence's summed TEMPO - the new per-word lean carried
 * by seint (-1), snoggt (+1), hradi (+0.5), and kyrra itself (-2):
 *
 *   tempo > 0            -> HASTE   "tidbinda snoggt sjalfan"
 *   tempo < 0            -> SLOW    "tidbinda seint thetta" / "... umhverf"
 *   tempo <= -1.5        -> STASIS  "kyrra thetta" (held outside time)
 *   tempo == 0           -> the binding has no direction; the cast fails
 *
 * TARGETING: accepts a bound mark (thetta), an area (umhverf - everyone
 * in range EXCEPT the caster, who is always excluded from their own area
 * casts by TargetResolver.resolveArea), or a free cast (marklaust) that
 * follows the caster's gaze and takes hold of whatever it finds along
 * the way, exactly like a bolt would - see the OfDirection branch below.
 *
 * KRINGLA: paired with an area scope, converts the working from an
 * instant snapshot (only whoever happened to be standing there at the
 * moment of casting) into a lingering bubble that follows the caster and
 * continuously affects anyone who enters it - see TemporalFieldManager.
 * The caster is never affected by their own field.
 *
 * Behavior reference: EBW's Haste (self speed buff), Slow Time /
 * Decelerate (slow everything near the caster - "tidbinda seint kringla
 * umhverf" is a proper Decelerate, not just an instant pulse), and
 * Arrest/Stasis (a mob held motionless - kyrra, ticked by StasisManager).
 *
 * Power (from verb precision + mikla/litla/ofsa) sets both the effect
 * amplifier and how long the binding holds; the |tempo| sum sharpens it.
 * Same security posture as everything else: tempo is a clamped number a
 * word can carry, never a behavior it can define.
 */
public class TemporalWorkingHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        4, 12.0f, 24f, Set.of(TargetKind.ENTITY, TargetKind.DIRECTION)
    );

    private static final float STASIS_THRESHOLD = -1.5f;
    private static final double MARKLAUST_RANGE = 24.0;

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("temporal_working");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        float tempo = tempoSum(invocation);
        int targets = Math.max(1, invocation.targets().size());
        boolean lingering = isLingering(invocation);
        // Holding something fully outside time is far harder than leaning
        // its flow one way or the other; a standing field that keeps
        // working on its own is harder again than a single instant touch.
        float base = (tempo <= STASIS_THRESHOLD ? 9f : 5f) * targets;
        return lingering ? base * 1.8f : base;
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

        float tempo = tempoSum(invocation);
        if (tempo == 0f) {
            return EffectResult.failure(
                "The binding takes hold of time but is given no direction - speak seint (slower) or snoggt (swifter).");
        }

        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst().map(Word::precision).orElse(0.5f);
        float power = 3f + verbPrecision * 4f + Math.abs(invocation.modifierMagnitudeSum()) * 3f;
        power = Math.max(1f, Math.min(power, CAPS.maxMagnitudePerTarget()));

        int amplifier = Math.max(0, Math.min(3, Math.round(power / 4f)));
        int duration = Math.round(20 * (8 + power * 2)); // 8s..~32s
        boolean stasis = tempo <= STASIS_THRESHOLD;

        // kringla + an area scope: a standing bubble, not a one-off pulse.
        // The caster carries it with them and is never affected by it.
        float scopeRadius = invocation.composition().scopeWord()
            .map(com.dragonspeech.word.Word::scopeRadius).orElse(0f);
        if (isLingering(invocation) && scopeRadius > 0f) {
            int fieldDuration = Math.round(20 * (6 + power * 1.5f));
            TemporalFieldManager.place(level, caster, scopeRadius, tempo, amplifier, stasis, fieldDuration);

            Vec3 fxPos = caster.position().add(0, 1, 0);
            SpellFx.flash(level, stasis ? 0xd8ccff : (tempo < 0 ? 0x6a5acd : 0xfff4b8), fxPos);

            return EffectResult.success(1, stasis
                ? "The word closes around you like a held breath, and a bubble of stillness spreads outward - you alone stand outside it."
                : "The word takes root around you, and the river's flow bends within its reach - you alone are untouched.");
        }

        int touched = 0;

        for (EffectTarget target : invocation.targets()) {
            LivingEntity living;

            if (target instanceof EffectTarget.OfEntity(Entity raw) && raw instanceof LivingEntity l) {
                living = l;
            } else if (target instanceof EffectTarget.OfDirection(Vec3 origin, Vec3 direction)) {
                // marklaust: the binding goes out along the gaze and takes
                // hold of whatever it first finds, exactly like a bolt.
                Strikes.StrikeHit hit = Strikes.ray(caster, origin, direction, MARKLAUST_RANGE);
                if (!(hit.entity() instanceof LivingEntity l)) {
                    continue; // nothing along the ray for the binding to catch
                }
                living = l;
            } else {
                continue;
            }

            Vec3 fxPos = living.position().add(0, living.getBbHeight() * 0.5, 0);

            if (stasis) {
                // kyrra: hold the mark outside time entirely. Mobs freeze
                // truly (StasisManager); players can't be frozen without
                // fighting the client, so they get the heaviest lean the
                // effect system allows instead.
                if (living instanceof Mob mob) {
                    StasisManager.hold(level, mob, Math.min(duration, 20 * 12 + Math.round(power * 20)));
                } else {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration / 2, 6));
                    living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration / 2, 3));
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration / 2, 1));
                }
                TimeSlowRegistry.mark(living.getUUID(), level.getGameTime(), duration);
                TimeSlowRegistry.stretchFireIfBurning(living, duration);
                SpellFx.flash(level, 0xd8ccff, fxPos);
                SpellFx.burst(level, DragonSpeechParticles.SPARKLE, 0xd8ccff, 0xffffff, fxPos, 16, 0.02);
            } else if (tempo < 0) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, Math.max(0, amplifier - 1)));
                TimeSlowRegistry.mark(living.getUUID(), level.getGameTime(), duration);
                TimeSlowRegistry.stretchFireIfBurning(living, duration);
                SpellFx.burst(level, DragonSpeechParticles.DUST, 0x6a5acd, 0x2a2050, fxPos, 10, 0.05);
                SpellFx.of(DragonSpeechParticles.PATH).pos(fxPos).color(0x6a5acd).time(30)
                    .count(6).jitter(0.6).spawn(level);
            } else {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, amplifier));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, duration, amplifier));
                SpellFx.spiral(level, DragonSpeechParticles.SPARKLE, 0xfff4b8, 0xffffff, living, 0.9);
            }
            touched++;
        }

        if (touched == 0) {
            return EffectResult.failure("Time will not bend around that.");
        }

        String message = stasis
            ? "The word closes like a fist, and time simply stops holding them."
            : tempo < 0
                ? "The word drags at the river of time, and their moments thicken like honey."
                : "The word runs ahead of the river, and the world slows around you.";
        return EffectResult.success(touched, message);
    }

    private static boolean isLingering(EffectInvocation invocation) {
        return invocation.composition().words().stream().anyMatch(w -> "kringla".equals(w.trueName()));
    }

    private static float tempoSum(EffectInvocation invocation) {
        float sum = 0f;
        for (Word word : invocation.composition().words()) {
            sum += word.tempo();
        }
        return Math.max(-2f, Math.min(2f, sum));
    }
}
