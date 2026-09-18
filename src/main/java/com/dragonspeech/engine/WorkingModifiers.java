package com.dragonspeech.engine;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Generic semantic modifiers that alter any compatible form without turning into named spells.
 * A modifier only acts when its Ancient-Language word was actually spoken.
 */
final class WorkingModifiers {
    private WorkingModifiers() {}

    static boolean compressed(WorkingContext ctx) {
        return ctx.invocation().composition().occurrencesOf("thrett") > 0;
    }

    /** A dense working occupies less space; it does not silently become a different form. */
    static double areaScale(WorkingContext ctx) {
        return compressed(ctx) ? 0.68 : 1.0;
    }

    /** Concentrating a working gives the smaller contact area a modest impact premium. */
    static float compressionPowerScale(WorkingContext ctx) {
        return compressed(ctx) ? 1.12f : 1.0f;
    }

    static boolean carriesMomentum(WorkingContext ctx) {
        return ctx.invocation().composition().occurrencesOf("ferdafl") > 0;
    }

    /**
     * ferdafl means the working carries motion into what it hits. Direction is intentionally
     * derived from the actual speaker-to-mark relationship so this remains meaningful for any
     * projectile/form rather than hard-coding Thunder Fist or another named ability.
     */
    static void applyMomentum(WorkingContext ctx, Entity target, float powerScale) {
        if (!carriesMomentum(ctx) || target == null) return;
        Vec3 from = ctx.caster().position().add(0, ctx.caster().getBbHeight() * 0.5, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0e-5) direction = ctx.direction();
        if (direction.lengthSqr() < 1.0e-5) return;
        direction = direction.normalize();
        double strength = Math.min(1.15, 0.22 + ctx.power() * 0.035 * Math.max(.35, powerScale));
        target.push(direction.x * strength, Math.max(.06, direction.y * strength + .08), direction.z * strength);
        target.hurtMarked = true;
    }

    static boolean orbits(WorkingContext ctx) {
        return ctx.invocation().composition().occurrencesOf("kringferd") > 0;
    }

    static boolean redirects(WorkingContext ctx) {
        return ctx.hasTargeting(TargetingStyle.REDIRECT);
    }
}
