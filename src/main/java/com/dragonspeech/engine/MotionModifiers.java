package com.dragonspeech.engine;

import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import net.minecraft.world.phys.Vec3;

/**
 * Cross-form motion instructions spoken as modifiers. They decorate/alter the chosen form instead
 * of replacing it: "sveira regnfalla" remains rain, while "sveira geisla" remains a ray.
 */
final class MotionModifiers {
    private MotionModifiers() {}

    static boolean spirals(WorkingContext ctx) {
        return ctx.invocation().composition().occurrencesOf("sveira") > 0;
    }

    static void emitSpiralPath(WorkingContext ctx, Vec3 from, Vec3 to, float radius, float turns, int lifetime) {
        if (!spirals(ctx) || from.distanceToSqr(to) < 1.0e-5) return;
        SpellBodyVfx.emit(ctx.level(), ctx.caster(), SpellBodyVfxType.SPIRAL, ctx.elements(), from, to,
            Math.max(.16f, radius), Math.max(1.0f, turns), lifetime);
    }

    static void emitOrbitPath(WorkingContext ctx, Vec3 from, Vec3 to, float radius, int lifetime) {
        if (!WorkingModifiers.orbits(ctx) || from.distanceToSqr(to) < 1.0e-5) return;
        SpellBodyVfx.emit(ctx.level(), ctx.caster(), SpellBodyVfxType.ORBIT, ctx.elements(), from, to,
            Math.max(.20f, radius), 2.0f + Math.min(4.0f, ctx.power() * .12f), lifetime);
    }

    static void emitRedirectPath(WorkingContext ctx, Vec3 from, Vec3 to, float bend, int lifetime) {
        if (!WorkingModifiers.redirects(ctx) || from.distanceToSqr(to) < 1.0e-5) return;
        SpellBodyVfx.emit(ctx.level(), ctx.caster(), SpellBodyVfxType.REDIRECT, ctx.elements(), from, to,
            Math.max(.35f, bend), .045f + ctx.power() * .004f, lifetime);
    }

    static void emitPathDecorators(WorkingContext ctx, Vec3 from, Vec3 to, float spiralRadius, float turns, int lifetime) {
        emitSpiralPath(ctx, from, to, spiralRadius, turns, lifetime);
        emitOrbitPath(ctx, from, to, spiralRadius * 1.18f, lifetime);
        emitRedirectPath(ctx, from, to, spiralRadius * 1.35f, lifetime);
    }
}
