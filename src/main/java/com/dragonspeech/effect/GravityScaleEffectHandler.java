package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.GravityFieldManager;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Backs the Force domain's gravity ladder (thyngja / thyngdbinda) - a
 * real, sustained gravity-multiplier working, not slow-falling or
 * levitation. See GravityFieldManager for the actual per-tick physics;
 * this class only turns the SENTENCE into a (multiplier, duration) pair.
 *
 * DIRECTION READS AS INTENSITY, NOT AIM: "nidra" (downward) makes gravity
 * HEAVIER, "uppa" (upward) makes it LIGHTER (at high enough magnitude,
 * actively floats the target up) - reusing the existing motion-direction
 * words for what they already evoke ("against the pull of the earth" /
 * "toward the deep") rather than adding yet another word. "mikla" /
 * "litla" / "ofsa" / etc. (already read via modifierMagnitudeSum, same
 * as every other handler) scale how strong that push is. No direction
 * word spoken defaults to a moderate HEAVY pull - "thyngdbinda thetta"
 * alone still does something reasonable rather than failing.
 */
public class GravityScaleEffectHandler implements EffectHandler {

    private static final float MAX_MAGNITUDE = 6.0f;
    private static final float MIN_MULTIPLIER = -1.0f;
    private static final float MAX_MULTIPLIER = 6.0f;
    private static final int BASE_DURATION_TICKS = 60;
    private static final int PRECISION_DURATION_BONUS_TICKS = 80;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        6, MAX_MAGNITUDE, 20f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("gravity_scale");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        float multiplier = computeMultiplier(invocation);
        float deviation = Math.abs(multiplier - 1.0f);
        return Math.max(deviation, 0.5f) * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        if (!(invocation.caster().level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        float multiplier = computeMultiplier(invocation);
        int durationTicks = computeDuration(invocation);

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living) {
                GravityFieldManager.apply(level, living, multiplier, durationTicks);
                spawnDirectionParticles(level, living, multiplier);
                affected++;
            }
        }

        if (affected == 0) {
            return EffectResult.failure("There is nothing there for the pull to take hold of.");
        }

        String message = multiplier > 1.0f
            ? "The pull of the earth answers, and grows heavier."
            : "The pull of the earth answers, and grows thin.";
        return EffectResult.success(multiplier, message);
    }

    private static float computeMultiplier(EffectInvocation invocation) {
        // thyngdleysa names the removal of weight directly. Bare = exactly zero gravity. Lesser
        // magnitude leaves some gravity behind; greater magnitude crosses through zero into an
        // upward/negative pull. This same word is also understood by hurled weapons as no-gravity.
        if (invocation.composition().occurrencesOf("thyngdleysa") > 0) {
            float removal = Math.max(0.05f, 1f + invocation.modifierMagnitudeSum());
            return clamp(1f - removal, MIN_MULTIPLIER, 1.0f);
        }

        List<String> tags = invocation.composition().directionTags();
        boolean up = tags.contains("up");
        boolean down = tags.contains("down");
        // Both or neither spoken: default to heavier - see class doc.
        float sign = (up && !down) ? -1f : 1f;

        float intensity = 1f + invocation.modifierMagnitudeSum();
        float multiplier = 1f + sign * intensity;
        return clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    private static int computeDuration(EffectInvocation invocation) {
        float verbPrecision = invocation.composition().wordsOf(WordCategory.VERB).stream()
            .findFirst()
            .map(com.dragonspeech.word.Word::precision)
            .orElse(0.5f);
        return Math.round(BASE_DURATION_TICKS + verbPrecision * PRECISION_DURATION_BONUS_TICKS);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Purple-gray motes streaming around the target in whichever way
     * gravity is now pulling on them - down for heavy, up for light -
     * a custom (non-vanilla) particle, see GravityParticle. Purely
     * cosmetic; GravityFieldManager's own per-tick velocity math is what
     * actually does the work.
     */
    private static void spawnDirectionParticles(ServerLevel level, LivingEntity target, float multiplier) {
        Vec3 direction = multiplier >= 1f ? new Vec3(0, -1, 0) : new Vec3(0, 1, 0);
        float intensity = clamp(Math.abs(multiplier - 1f), 0.3f, 3f);
        var random = level.getRandom();

        for (int i = 0; i < 10; i++) {
            double offsetX = (random.nextDouble() - 0.5) * target.getBbWidth();
            double offsetZ = (random.nextDouble() - 0.5) * target.getBbWidth();
            double offsetY = random.nextDouble() * target.getBbHeight();
            Vec3 vel = direction.scale(0.06 * intensity)
                .add((random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02);

            SpellFx.of(DragonSpeechParticles.GRAVITY)
                .pos(target.getX() + offsetX, target.getY() + offsetY, target.getZ() + offsetZ)
                .vel(vel)
                .time(20)
                .spawn(level);
        }
    }
}
