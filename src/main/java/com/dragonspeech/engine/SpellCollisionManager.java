package com.dragonspeech.engine;

import com.dragonspeech.entity.MagicBarrierEntity;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import com.dragonspeech.fx.SpellFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight server-side collision registry for Dragon Speech's virtual spell bodies.
 *
 * Most composed bolts/rays/lances resolve as server raycasts instead of persistent entities, so
 * vanilla projectile collision cannot make them meet one another.  This registry gives those
 * bodies a short-lived mathematical presence: later/intersecting workings can clash with them,
 * and every path is tested against real MagicBarrierEntity volumes before damage is applied.
 *
 * It is intentionally a foundation, not a fake promise that every historical handler is already
 * physical.  The composed projectile family uses it now; more forms can opt in through resolvePath.
 */
public final class SpellCollisionManager {
    public record PathResult(Vec3 end, boolean blocked, float powerScale, boolean collided) {}

    private static final class Trace {
        long id;
        ServerLevel level;
        UUID owner;
        Vec3 start, end;
        float radius, power;
        List<Element> elements;
        long expiresAt;
        boolean active = true;
    }

    private static final List<Trace> TRACES = new ArrayList<>();
    private static final AtomicLong IDS = new AtomicLong(1);

    private SpellCollisionManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.getTickCount();
            TRACES.removeIf(t -> !t.active || t.expiresAt <= now);
        });
    }

    public static PathResult resolvePath(WorkingContext ctx, Vec3 start, Vec3 intendedEnd,
                                         float radius, float power, int traceLifetimeTicks) {
        return resolvePathInternal(ctx, start, intendedEnd, radius, power, traceLifetimeTicks, true);
    }

    /**
     * Internal path resolver. Reflected paths deliberately run with allowReflection=false so two
     * facing reflective barriers cannot recurse forever in a single server tick. The returned
     * path still collides with ordinary barriers and spell traces; it simply cannot immediately
     * reflect a second time.
     */
    private static PathResult resolvePathInternal(WorkingContext ctx, Vec3 start, Vec3 intendedEnd,
                                                   float radius, float power, int traceLifetimeTicks,
                                                   boolean allowReflection) {
        ServerLevel level = ctx.level();
        long now = level.getServer().getTickCount();
        cleanup(now);

        // Compression is not merely a damage modifier: a tightly concentrated working puts more
        // pressure on another spell body or barrier as well. This is the compositional equivalent
        // of Dragon Flux's Barrier Break without adding a fixed ability.
        power *= WorkingModifiers.compressionPowerScale(ctx);

        Vec3 end = intendedEnd;
        float scale = 1f;
        boolean collided = false;

        // Find the nearest opposing virtual spell body along this path.
        Trace bestTrace = null;
        Vec3 traceHit = null;
        double traceAlong = Double.MAX_VALUE;
        for (Trace other : TRACES) {
            if (!other.active || other.level != level || other.owner.equals(ctx.caster().getUUID())) continue;
            double threshold = Math.max(.08, radius) + other.radius + .15;
            ClosestPair pair = closestSegments(start, intendedEnd, other.start, other.end);
            if (pair.distance > threshold) continue;
            double along = start.distanceToSqr(pair.a);
            if (along < traceAlong) {
                traceAlong = along;
                bestTrace = other;
                traceHit = pair.a.lerp(pair.b, .5);
            }
        }

        // Find the nearest real barrier volume. Own barriers allow own magic to leave them.
        MagicBarrierEntity bestBarrier = null;
        Vec3 barrierHit = null;
        double barrierAlong = Double.MAX_VALUE;
        AABB search = new AABB(start, intendedEnd).inflate(radius + 1.0);
        for (MagicBarrierEntity barrier : level.getEntitiesOfClass(MagicBarrierEntity.class, search,
            b -> !b.isRemoved() && (b.casterId() == null || !b.casterId().equals(ctx.caster().getUUID())))) {
            Vec3 hit = firstAabbHit(start, intendedEnd, barrier.getBoundingBox().inflate(radius));
            if (hit == null) continue;
            double along = start.distanceToSqr(hit);
            if (along < barrierAlong) {
                barrierAlong = along;
                bestBarrier = barrier;
                barrierHit = hit;
            }
        }

        boolean traceFirst = bestTrace != null && (bestBarrier == null || traceAlong <= barrierAlong);
        if (traceFirst) {
            collided = true;
            float ours = Math.max(.1f, power);
            float theirs = Math.max(.1f, bestTrace.power);
            float hi = Math.max(ours, theirs), lo = Math.min(ours, theirs);
            clashFx(level, ctx, traceHit, Math.max(radius, bestTrace.radius));
            if (hi / Math.max(.001f, lo) < 1.15f) {
                bestTrace.active = false;
                end = traceHit;
                registerTrace(ctx, start, end, radius, power * .25f, traceLifetimeTicks);
                return new PathResult(end, true, .25f, true);
            }
            if (ours > theirs) {
                bestTrace.active = false;
                scale *= Math.max(.45f, 1f - .45f * (theirs / ours));
            } else {
                end = traceHit;
                registerTrace(ctx, start, end, radius, power * .35f, traceLifetimeTicks);
                return new PathResult(end, true, .35f, true);
            }
        }

        // If a barrier is the first obstruction (or the spell won an earlier clash and then reaches
        // the barrier), pressure it.  Surviving barriers stop the spell; a shattered barrier lets a
        // weakened remainder continue through.
        if (bestBarrier != null && (!traceFirst || barrierAlong >= traceAlong)) {
            collided = true;
            boolean survives = bestBarrier.absorbSpellImpact(power * scale, ctx.elements(), barrierHit);
            clashFx(level, ctx, barrierHit, Math.max(radius, .35f));
            if (survives) {
                end = barrierHit;
                registerTrace(ctx, start, end, radius, power * scale, traceLifetimeTicks);
                if (allowReflection && bestBarrier.reflective()) {
                    reflectSpell(bestBarrier, ctx, barrierHit, radius, power * scale, traceLifetimeTicks);
                }
                return new PathResult(end, true, scale, true);
            }
            scale *= .55f;
        }

        registerTrace(ctx, start, end, radius, power * scale, traceLifetimeTicks);
        return new PathResult(end, false, scale, collided);
    }


    /**
     * Reflect a blocked virtual spell back toward its original caster. The reflected segment is
     * resolved through the SAME collision system, so the original caster's own barrier (or a third
     * spell body) can stop the return. Reflection retains about 70% of the impact power and is
     * attributed to the barrier's owner when that owner is an online player.
     */
    private static void reflectSpell(MagicBarrierEntity barrier, WorkingContext incoming, Vec3 hit,
                                     float radius, float incomingPower, int traceLifetimeTicks) {
        UUID ownerId = barrier.casterId();
        if (ownerId == null) return;
        ServerPlayer reflector = incoming.level().getServer().getPlayerList().getPlayer(ownerId);
        if (reflector == null || reflector == incoming.caster()) return;

        Vec3 target = incoming.caster().position().add(0, incoming.caster().getBbHeight() * 0.55, 0);
        Vec3 direction = target.subtract(hit);
        if (direction.lengthSqr() < 1.0e-8) return;
        direction = direction.normalize();
        Vec3 reflectedStart = hit.add(direction.scale(0.12));

        WorkingContext reflected = new WorkingContext(
            reflector, incoming.level(), incoming.elements(), incoming.invocation(),
            incoming.power(), incoming.count(), incoming.targeting(), direction,
            incoming.scopeRadius(), List.of(WorkingContext.Anchor.of(incoming.caster()))
        );

        float reflectedPower = Math.max(.1f, incomingPower * .70f);
        float reflectedCompression = Math.max(.001f, WorkingModifiers.compressionPowerScale(reflected));
        PathResult returnPath = resolvePathInternal(
            reflected, reflectedStart, target, Math.max(.06f, radius * .9f),
            reflectedPower / reflectedCompression, Math.max(2, traceLifetimeTicks), false
        );

        SpellBodyVfx.emit(incoming.level(), reflector, SpellBodyVfxType.REDIRECT, incoming.elements(),
            reflectedStart, returnPath.end(), Math.max(.7f, radius * 2f), 0f, 7);

        if (!returnPath.blocked()) {
            float split = incoming.elements().size() <= 1 ? 1f : (float) (1.0 / Math.sqrt(incoming.elements().size()));
            for (Element element : incoming.elements()) {
                element.hitEntity(reflector, incoming.caster(), reflectedPower * returnPath.powerScale() * split);
            }
        }
    }

    private static void registerTrace(WorkingContext ctx, Vec3 start, Vec3 end, float radius, float power, int life) {
        Trace t = new Trace();
        t.id = IDS.getAndIncrement();
        t.level = ctx.level();
        t.owner = ctx.caster().getUUID();
        t.start = start;
        t.end = end;
        t.radius = Math.max(.06f, radius);
        t.power = Math.max(.1f, power);
        t.elements = List.copyOf(ctx.elements());
        t.expiresAt = ctx.level().getServer().getTickCount() + Math.max(2, life);
        TRACES.add(t);
    }

    private static void cleanup(long now) {
        TRACES.removeIf(t -> !t.active || t.expiresAt <= now);
    }

    private static void clashFx(ServerLevel level, WorkingContext ctx, Vec3 p, float scale) {
        int color = ctx.elements().isEmpty() ? 0x9fe7d0 : ctx.elements().get(0).color();
        SpellFx.burst(level, DragonSpeechParticles.SPARKLE, color, 0xffffff, p, 15, Math.max(.12, scale * .22));
        SpellFx.flash(level, color, p);
    }

    /** Segment-vs-AABB slab intersection, returns the first hit from start or null. */
    private static Vec3 firstAabbHit(Vec3 start, Vec3 end, AABB box) {
        Vec3 d = end.subtract(start);
        double tMin = 0.0, tMax = 1.0;
        double[] s = {start.x, start.y, start.z};
        double[] v = {d.x, d.y, d.z};
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(v[i]) < 1e-9) {
                if (s[i] < min[i] || s[i] > max[i]) return null;
                continue;
            }
            double a = (min[i] - s[i]) / v[i];
            double b = (max[i] - s[i]) / v[i];
            if (a > b) { double q = a; a = b; b = q; }
            tMin = Math.max(tMin, a);
            tMax = Math.min(tMax, b);
            if (tMin > tMax) return null;
        }
        return start.add(d.scale(Math.max(0, Math.min(1, tMin))));
    }

    private record ClosestPair(Vec3 a, Vec3 b, double distance) {}

    /** Closest points between two finite 3D segments. */
    private static ClosestPair closestSegments(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
        Vec3 d1 = q1.subtract(p1), d2 = q2.subtract(p2), r = p1.subtract(p2);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        double s, t;
        if (a <= 1e-9 && e <= 1e-9) return new ClosestPair(p1, p2, p1.distanceTo(p2));
        if (a <= 1e-9) {
            s = 0; t = clamp(f / e);
        } else {
            double c = d1.dot(r);
            if (e <= 1e-9) {
                t = 0; s = clamp(-c / a);
            } else {
                double b = d1.dot(d2), denom = a * e - b * b;
                s = denom != 0 ? clamp((b * f - c * e) / denom) : 0;
                t = (b * s + f) / e;
                if (t < 0) { t = 0; s = clamp(-c / a); }
                else if (t > 1) { t = 1; s = clamp((b - c) / a); }
            }
        }
        Vec3 aPoint = p1.add(d1.scale(s)), bPoint = p2.add(d2.scale(t));
        return new ClosestPair(aPoint, bPoint, aPoint.distanceTo(bPoint));
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
