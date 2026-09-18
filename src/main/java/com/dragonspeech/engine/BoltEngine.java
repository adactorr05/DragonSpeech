package com.dragonspeech.engine;

import com.dragonspeech.effect.EffectResult;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * BOLT (kasta and every element-verb's default): a hurled strike along a
 * line. Behavior reference: EBW Firebolt / Ice Shard / Lightning Bolt /
 * Force Arrow - here they're one engine whose element, count, homing and
 * chaining all come from the sentence.
 *
 * - margfalt fires `count` bolts with a slight spread
 * - leitbinda bends each bolt toward the nearest mark near its path
 * - kedjubinda makes each struck mark arc onward to further marks
 * - marklaust + direction words fire bolts with no bound target at all
 *
 * Bolts resolve instantly (a raycast plus a travelling particle trail)
 * rather than spawning a physical projectile entity - the trail reads as
 * a projectile in play, with none of the entity/renderer plumbing. If a
 * true slow-flying projectile is ever wanted, it belongs in a follow-up
 * entity-based engine, not bolted onto this one.
 */
public class BoltEngine implements FormEngine {

    private static final double BOLT_RANGE = 32.0;

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();
        Set<Entity> alreadyStruck = new HashSet<>();
        int struck = 0;

        for (WorkingContext.Anchor anchor : ctx.anchors()) {
            for (int i = 0; i < ctx.count(); i++) {
                Vec3 origin = ctx.origin();

                // Aim at the anchor; free (marklaust) anchors aim along the
                // spoken/looked direction with a touch of spread per extra bolt.
                Vec3 aim = anchor.entity() != null
                    ? anchor.pos().subtract(origin)
                    : ctx.handAim(BOLT_RANGE);
                if (i > 0) {
                    aim = jitter(ctx, aim, 0.12);
                }
                if (aim.lengthSqr() < 0.0001) {
                    aim = ctx.direction();
                }

                Strikes.StrikeHit hit;
                if (anchor.entity() != null && i == 0) {
                    // A bound target was spoken ("thetta" on a mob): the first
                    // bolt strikes it directly, no re-aim.
                    hit = new Strikes.StrikeHit(anchor.pos(), anchor.entity(), null);
                } else {
                    hit = Strikes.ray(ctx.caster(), origin, aim, BOLT_RANGE);
                }

                // leitbinda: if this bolt found no mark, bend it toward one.
                if (hit.entity() == null && ctx.hasTargeting(TargetingStyle.HOMING)) {
                    LivingEntity sought = Strikes.seek(ctx.caster(), origin, aim, alreadyStruck);
                    if (sought != null) {
                        hit = new Strikes.StrikeHit(sought.position().add(0, sought.getBbHeight() * 0.5, 0), sought, null);
                    }
                }

                SpellCollisionManager.PathResult collision = SpellCollisionManager.resolvePath(
                    ctx, origin, hit.pos(), .16f, ctx.power(), 8);
                Vec3 visualEnd = collision.end();

                boolean hasLightning = ctx.elements().contains(Element.LIGHTNING);
                var bodyElements = ctx.elements().stream().filter(e -> e != Element.LIGHTNING).toList();
                int bodyLife = 8;

                // Lightning has one canonical travel body: the same custom forked LIGHTNING renderer
                // used by kedjubinda's chain jumps.  Do not stack a second generic lightning body on top.
                // In a woven working (fire + lightning, ice + lightning, etc.) the non-lightning element
                // keeps its own rendered body and lightning is layered exactly once as the forked channel.
                if (!bodyElements.isEmpty()) {
                    SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.BOLT, bodyElements, origin, visualEnd,
                        .14f + ctx.power() * .012f, 0f, bodyLife);
                }
                if (hasLightning) {
                    SpellFx.arc(level, Element.LIGHTNING.color(), ctx.caster(), origin, visualEnd, 6);
                }
                if (!hasLightning || !bodyElements.isEmpty()) {
                    MotionModifiers.emitPathDecorators(ctx, origin, visualEnd, .32f + ctx.power() * .018f, 3.0f, bodyLife);
                }
                // No legacy travel-particle line here: custom geometry / forked lightning IS the projectile.

                if (collision.blocked()) {
                    ctx.impactFx(visualEnd);
                    struck++;
                    continue;
                }

                if (hit.entity() != null) {
                    ctx.hitEntity(hit.entity(), collision.powerScale());
                    ctx.impactFx(hit.pos());
                    alreadyStruck.add(hit.entity());
                    struck++;

                    if (ctx.hasTargeting(TargetingStyle.CHAIN)) {
                        struck += Strikes.chain(ctx, hit.entity(), Math.max(2, ctx.count()));
                    }
                } else if (hit.blockPos() != null) {
                    ctx.hitBlock(hit.blockPos(), 1f);
                    ctx.impactFx(hit.pos());
                    struck++;
                } else {
                    // Fired into open air (marklaust) - the bolt spends itself
                    // at the end of its flight.
                    SpellFx.burst(level, DragonSpeechParticles.SPARKLE, Strikes.primaryColor(ctx), 0xffffff, hit.pos(), 6, 0.08);
                }
            }
        }

        return EffectResult.success(struck, "The word leaves your lips, and the working flies.");
    }

    private static Vec3 jitter(WorkingContext ctx, Vec3 aim, double amount) {
        var random = ctx.level().getRandom();
        return aim.normalize().add(
            (random.nextDouble() * 2 - 1) * amount,
            (random.nextDouble() * 2 - 1) * amount,
            (random.nextDouble() * 2 - 1) * amount
        );
    }
}

/**
 * RAY (geisla): a straight piercing beam that touches everything along
 * it. Behavior reference: EBW Frost Ray / Flame Ray / Lightning Ray.
 * Every living mark along the line is struck at reduced power (a wide
 * touch is shallower than a focused one); the beam itself is one
 * stretched beam particle plus sparkles along its length.
 */
class RayEngine implements FormEngine {

    private static final double RAY_RANGE = 16.0;
    private static final float PIERCE_POWER_SCALE = 0.65f;

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();
        Vec3 origin = ctx.origin();

        // A bound target bends the ray toward it; otherwise it follows the
        // spoken direction / the caster's gaze.
        Vec3 aim = ctx.anchors().isEmpty() || ctx.anchors().get(0).entity() == null
            ? ctx.handAim(RAY_RANGE)
            : ctx.anchors().get(0).pos().subtract(origin);
        if (aim.lengthSqr() < 0.0001) {
            aim = ctx.direction();
        }
        aim = aim.normalize();

        // The ray stops at the first solid block face.
        Strikes.StrikeHit wall = Strikes.ray(ctx.caster(), origin, aim, RAY_RANGE);
        Vec3 end = wall.blockPos() != null ? wall.pos() : origin.add(aim.scale(RAY_RANGE));
        SpellCollisionManager.PathResult collision = SpellCollisionManager.resolvePath(
            ctx, origin, end, (float)(.10 * WorkingModifiers.areaScale(ctx)), ctx.power(),
            ctx.invocation().composition().hasContinuousModifier() ? 12 : 7);
        end = collision.end();
        double length = origin.distanceTo(end);

        int struck = 0;
        Set<Entity> alreadyStruck = new HashSet<>();
        double rayWidth = 0.8 * WorkingModifiers.areaScale(ctx);
        AABB sweep = new AABB(origin, end).inflate(rayWidth);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep,
            e -> e != ctx.caster() && e.isAlive() && !e.isSpectator())) {

            Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 toTarget = center.subtract(origin);
            double along = toTarget.dot(aim);
            if (along < 0 || along > length) {
                continue;
            }
            double offLine = toTarget.subtract(aim.scale(along)).length();
            if (offLine > 1.0 * WorkingModifiers.areaScale(ctx)) {
                continue;
            }

            ctx.hitEntity(target, PIERCE_POWER_SCALE * collision.powerScale());
            ctx.impactFx(center);
            alreadyStruck.add(target);
            struck++;

            if (ctx.hasTargeting(TargetingStyle.CHAIN)) {
                struck += Strikes.chain(ctx, target, 2);
            }
        }

        if (!collision.blocked() && wall.blockPos() != null) {
            ctx.hitBlock(wall.blockPos(), collision.powerScale());
        }

        int rayLife = ctx.invocation().composition().hasContinuousModifier() ? 28 : 10;
        boolean hasLightning = ctx.elements().contains(Element.LIGHTNING);
        var bodyElements = ctx.elements().stream().filter(e -> e != Element.LIGHTNING).toList();
        if (!bodyElements.isEmpty()) {
            SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.RAY, bodyElements, origin, end,
                .055f + ctx.power() * .006f, 0f, rayLife);
        }
        if (hasLightning) {
            // Same authored forked channel as chain lightning; no second lightning beam/trail layered over it.
            SpellFx.arc(level, Element.LIGHTNING.color(), ctx.caster(), origin, end,
                ctx.invocation().composition().hasContinuousModifier() ? 24 : 8);
        }
        if (!hasLightning || !bodyElements.isEmpty()) {
            MotionModifiers.emitPathDecorators(ctx, origin, end, .24f + ctx.power() * .014f, 4.0f, rayLife);
        }

        return EffectResult.success(struck, "The word holds steady, and the working burns a straight line through the air.");
    }
}

/**
 * ORB (kula): a slow gathered sphere that bursts softly where it lands,
 * touching everything close by. Behavior reference: EBW's orb-style
 * projectiles (Spark Bomb, Poison Bomb, Ice Charge) - a lobbed sphere
 * whose payload is a small area impact rather than a piercing hit.
 */
class OrbEngine implements FormEngine {

    private static final double ORB_RANGE = 24.0;
    private static final double SPLASH_RADIUS = 2.5;
    private static final float SPLASH_POWER_SCALE = 0.7f;

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();
        int struck = 0;

        for (WorkingContext.Anchor anchor : ctx.anchors()) {
            for (int i = 0; i < ctx.count(); i++) {
                Vec3 origin = ctx.origin();
                Vec3 aim = anchor.entity() != null ? anchor.pos().subtract(origin) : ctx.handAim(ORB_RANGE);
                if (aim.lengthSqr() < 0.0001) {
                    aim = ctx.direction();
                }

                Strikes.StrikeHit hit = anchor.entity() != null && i == 0
                    ? new Strikes.StrikeHit(anchor.pos(), anchor.entity(), null)
                    : Strikes.ray(ctx.caster(), origin, aim, ORB_RANGE);

                if (hit.entity() == null && ctx.hasTargeting(TargetingStyle.HOMING)) {
                    LivingEntity sought = Strikes.seek(ctx.caster(), origin, aim, Set.of());
                    if (sought != null) {
                        hit = new Strikes.StrikeHit(sought.position().add(0, sought.getBbHeight() * 0.5, 0), sought, null);
                    }
                }

                SpellCollisionManager.PathResult collision = SpellCollisionManager.resolvePath(
                    ctx, origin, hit.pos(), .28f, ctx.power(), 10);
                Vec3 impact = collision.end();

                SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.ORB, ctx.elements(), origin, impact,
                    .20f + ctx.power() * .018f, 0f, 9);
                MotionModifiers.emitPathDecorators(ctx, origin, impact, .38f + ctx.power() * .020f, 2.5f, 9);
                // The rendered orb is the travel body; old line-of-particles travel FX are intentionally removed.
                int color = Strikes.primaryColor(ctx);

                // The soft burst on arrival.
                SpellFx.sphere(level, color, impact, 0.35f + ctx.power() * 0.03f);
                if (!collision.blocked()) {
                    for (LivingEntity target : ctx.livingWithin(impact, SPLASH_RADIUS * WorkingModifiers.areaScale(ctx))) {
                        ctx.hitEntity(target, SPLASH_POWER_SCALE * collision.powerScale());
                        struck++;
                    }
                    if (hit.blockPos() != null) {
                        ctx.hitBlock(hit.blockPos(), collision.powerScale());
                    }
                } else {
                    struck++;
                }
                ctx.impactFx(impact);
            }
        }

        return EffectResult.success(struck, "The working gathers itself into a sphere and drifts to its mark.");
    }
}

/** Package-private helper so ElementalWorkingHandler can construct every projectile-family engine from one import. */
final class ProjectileEngines {
    static final FormEngine BOLT = new BoltEngine();
    static final FormEngine RAY = new RayEngine();
    static final FormEngine ORB = new OrbEngine();

    private ProjectileEngines() {}

    static BlockPos blockBelow(Vec3 pos) {
        return BlockPos.containing(pos.x, pos.y - 0.5, pos.z);
    }
}
