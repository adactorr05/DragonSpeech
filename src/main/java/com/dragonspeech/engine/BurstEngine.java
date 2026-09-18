package com.dragonspeech.engine;

import com.dragonspeech.effect.EffectResult;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import com.dragonspeech.entity.MagicBarrierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BURST (sprengja): an outward explosion at the point of impact - no
 * block destruction, but everything close is struck and shoved.
 * Behavior reference: EBW Detonate / the "blast" family, minus terrain
 * damage (Dragon Speech's hard-cap philosophy: a wording can never grief
 * terrain harder than its handler allows).
 */
public class BurstEngine implements FormEngine {

    private static final double BASE_RADIUS = 3.0;
    private static final float BURST_POWER_SCALE = 0.85f;

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();
        int struck = 0;

        boolean barrierPulse = ctx.invocation().composition().occurrencesOf("med") > 0
            && ctx.invocation().composition().occurrencesOf("varn") > 0;
        List<Vec3> centers = new ArrayList<>();
        if (barrierPulse) {
            Optional<MagicBarrierEntity> source = MagicBarrierEntity.findBarrierInSight(level, ctx.caster(), 28.0, true);
            if (source.isEmpty()) source = MagicBarrierEntity.findPersonalShieldFor(level, ctx.caster().getUUID());
            if (source.isEmpty()) source = MagicBarrierEntity.findOwnedBarrierNear(level, ctx.caster().getUUID(), ctx.caster().position(), 28.0);
            if (source.isEmpty()) {
                return EffectResult.failure("No barrier of yours answers as the source of the burst.");
            }
            centers.add(source.get().position());
        } else {
            for (WorkingContext.Anchor anchor : ctx.anchors()) {
                // A direction cast (marklaust) bursts where the ray lands.
                Vec3 center = anchor.entity() != null ? anchor.pos()
                    : Strikes.ray(ctx.caster(), ctx.origin(), anchor.pos().subtract(ctx.origin()).lengthSqr() > 0.01
                        ? anchor.pos().subtract(ctx.origin()) : ctx.direction(), 24.0).pos();
                centers.add(center);
            }
        }

        for (Vec3 center : centers) {
            double radius = (BASE_RADIUS + ctx.power() * 0.15 + (ctx.count() - 1) * 0.5) * WorkingModifiers.areaScale(ctx);

            for (LivingEntity target : ctx.livingWithin(center, radius)) {
                ctx.hitEntity(target, BURST_POWER_SCALE);
                Vec3 away = target.position().subtract(center);
                Vec3 flat = new Vec3(away.x, 0, away.z);
                if (flat.lengthSqr() > 0.0001) {
                    flat = flat.normalize().scale(0.6 + ctx.power() * 0.05);
                    target.push(flat.x, 0.35, flat.z);
                    target.hurtMarked = true;
                }
                struck++;
            }

            ctx.hitBlock(BlockPos.containing(center.x, center.y, center.z), 1f);

            int color = Strikes.primaryColor(ctx);
            SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.BURST, ctx.elements(), center, center,
                (float)radius, 0f, 12);
            SpellFx.sphere(level, color, center, 0.4f + (float) radius * 0.12f);
            SpellFx.flash(level, color, center);
            for (Element element : ctx.elements()) {
                SpellFx.burst(level, element.trailParticle(), element.color(), element.fadeColor(), center, 24, 0.35);
            }
        }

        return EffectResult.success(struck, barrierPulse
            ? "The standing barrier answers as a source, and the working bursts outward from it."
            : "The word bursts outward, and the air itself recoils.");
    }
}

/**
 * RING (hringr): the working drawn in a circle around a centre - the
 * caster by default, or the bound target/landing point if one was spoken.
 * Behavior reference: EBW Ring of Fire / Frost Barrier's circular shape:
 * everything standing in the ring band is struck, and the ring's ground
 * is marked (fire element leaves a burning circle, ice a frosted one).
 */
class RingEngine implements FormEngine {

    private static final float RING_POWER_SCALE = 0.8f;

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();

        // scope word widens the circle: "hringr eldr" is a close ring,
        // "hringr eldr umhverf" pushes it out to the scope's reach.
        double radius = (ctx.scopeRadius() > 0 ? ctx.scopeRadius() : 3.5) * WorkingModifiers.areaScale(ctx);

        Vec3 center;
        if (ctx.anchors().isEmpty()) {
            center = ctx.caster().position();
        } else if (ctx.anchors().get(0).entity() != null) {
            center = ctx.anchors().get(0).entity().position();
        } else if (ctx.invocation().composition().isTargetless()) {
            // marklaust names no bound mark, so place the ring where the gaze actually lands
            // rather than silently snapping it back around the caster.
            center = Strikes.ray(ctx.caster(), ctx.gazeOrigin(), ctx.direction(), 20.0).pos();
        } else {
            // A block/point target is already a real named mark.
            center = ctx.anchors().get(0).pos();
        }

        boolean cuttingRing = ctx.invocation().composition().occurrencesOf("sveira") > 0
            && ctx.invocation().composition().occurrencesOf("hvassa") > 0;
        if (cuttingRing) {
            // hringr + sveira + hvassa: the existing ring itself is asked to revolve with a biting
            // edge. With no kasta this remains a stationary field rather than secretly becoming
            // a projectile. A tiny vertical axis gives the client a horizontal disc orientation.
            SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.CUTTING_RING, ctx.elements(),
                center.add(0, -.01, 0), center.add(0, .01, 0), (float)radius, .060f, 24);
        } else {
            SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.RING, ctx.elements(), center, center,
                (float)radius, .055f, 24);
        }
        int points = Math.max(10, (int) (radius * 5));
        int struck = 0;

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            Vec3 rim = center.add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);
            Vec3 ground = Strikes.dropToGround(level, ctx.caster(), rim, 4);

            ctx.hitBlock(ProjectileEngines.blockBelow(ground.add(0, 0.1, 0)), 1f);
            for (Element element : ctx.elements()) {
                SpellFx.burst(level, element.trailParticle(), element.color(), element.fadeColor(), ground.add(0, 0.3, 0), 3, 0.05);
            }
        }

        // Strike everything standing in the ring band (not the safe middle).
        for (LivingEntity target : ctx.livingWithin(center, radius + 1.0)) {
            double distance = new Vec3(target.getX() - center.x, 0, target.getZ() - center.z).length();
            if (distance >= radius - 1.2) {
                ctx.hitEntity(target, cuttingRing ? 1.05f : RING_POWER_SCALE);
                if (cuttingRing) {
                    Vec3 radial = new Vec3(target.getX() - center.x, 0, target.getZ() - center.z);
                    if (radial.lengthSqr() > 1.0e-6) {
                        Vec3 tangential = new Vec3(-radial.z, 0, radial.x).normalize().scale(.22 + ctx.power() * .008);
                        target.push(tangential.x, .06, tangential.z);
                        target.hurtMarked = true;
                    }
                }
                ctx.impactFx(target.position().add(0, target.getBbHeight() * 0.5, 0));
                struck++;
            }
        }

        return EffectResult.success(struck, "The word closes like a drawn circle, and the ring answers.");
    }
}

/**
 * RAIN (regnfalla): scattered strikes falling from the sky over an area.
 * Behavior reference: EBW Firestorm / Hailstorm / Arrow Rain - the same
 * descending-strike pattern, with the element deciding what each falling
 * strike does and margfalt deciding how many fall.
 *
 * The wording places the storm: over the bound target if one was spoken,
 * over where the gaze/direction lands with marklaust, and the scope word
 * sets how wide the storm spreads.
 */
class RainEngine implements FormEngine {

    private static final int BASE_STRIKES = 6;
    private static final int MAX_STRIKES = 16;
    private static final double DROP_HEIGHT = 11.0;
    private static final float RAIN_POWER_SCALE = 0.7f;

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();
        RandomSource random = level.getRandom();

        Vec3 center;
        if (!ctx.anchors().isEmpty() && ctx.anchors().get(0).entity() != null) {
            center = ctx.anchors().get(0).pos();
        } else if (!ctx.anchors().isEmpty()) {
            // Free/direction cast: the storm gathers where the gaze lands.
            // Placement is chosen from the CAMERA gaze so the storm lands where the crosshair is,
            // while the falling VFX itself is independent of the camera/hand origin.
            center = Strikes.ray(ctx.caster(), ctx.gazeOrigin(), ctx.direction(), 20.0).pos();
        } else {
            center = ctx.caster().position();
        }

        double radius = (ctx.scopeRadius() > 0 ? ctx.scopeRadius() : 4.5) * WorkingModifiers.areaScale(ctx);
        int strikes = Math.min(MAX_STRIKES, BASE_STRIKES + (ctx.count() - 1) * 3 + Math.round(ctx.power() * 0.3f));

        int struck = 0;
        boolean spiralRain = MotionModifiers.spirals(ctx);
        // sveira modifies the rain pattern instead of replacing RAIN with a separate spell form.
        // Golden-angle spacing keeps the descending strikes readable while winding from the centre outward.
        final double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < strikes; i++) {
            double angle;
            double distance;
            if (spiralRain) {
                double f = strikes <= 1 ? 0.0 : i / (double) (strikes - 1);
                angle = i * goldenAngle;
                distance = radius * Math.sqrt(f);
            } else {
                angle = random.nextDouble() * Math.PI * 2;
                distance = Math.sqrt(random.nextDouble()) * radius;
            }
            Vec3 sky = new Vec3(center.x + Math.cos(angle) * distance, center.y + DROP_HEIGHT, center.z + Math.sin(angle) * distance);
            Vec3 intendedGround = Strikes.dropToGround(level, ctx.caster(), sky, DROP_HEIGHT * 2);
            SpellCollisionManager.PathResult collision = SpellCollisionManager.resolvePath(
                ctx, sky, intendedGround, .11f, ctx.power() * RAIN_POWER_SCALE, 7);
            Vec3 ground = collision.end();

            // RAIN_STRIKE is a MOVING droplet/shard/comet, not a full-height beam. secondary=1 tells
            // the client that sveira was spoken so the falling body corkscrews on the way down.
            int fallLife = 10 + random.nextInt(5);
            SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.RAIN_STRIKE, ctx.elements(), sky, ground,
                .10f + ctx.power() * .006f, spiralRain ? 1f : 0f, fallLife);
            ctx.impactFx(ground.add(0, 0.2, 0));
            if (collision.blocked()) {
                continue;
            }
            ctx.hitBlock(ProjectileEngines.blockBelow(ground.add(0, 0.1, 0)), collision.powerScale());

            for (LivingEntity target : ctx.livingWithin(ground.add(0, 0.5, 0), 1.6)) {
                ctx.hitEntity(target, RAIN_POWER_SCALE * collision.powerScale());
                struck++;
            }
        }

        // A coherent cloud body overhead makes this read as weather instead of "beams from the sky".
        Vec3 cloudCenter = center.add(0, DROP_HEIGHT - 1.0, 0);
        SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.CLOUD, ctx.elements(), cloudCenter, cloudCenter,
            (float)Math.max(1.2, radius * .82), spiralRain ? 1f : 0f, 24);
        // A handful of custom cloud motes add atmosphere without becoming the main spell body.
        SpellFx.of(DragonSpeechParticles.CLOUD)
            .pos(cloudCenter)
            .color(Strikes.primaryColor(ctx))
            .count(Math.min(5, Math.max(2, strikes / 3)))
            .jitter(radius * 0.55)
            .spawn(level);

        return EffectResult.success(struck, "The sky takes up the word, and the working falls like rain.");
    }
}
