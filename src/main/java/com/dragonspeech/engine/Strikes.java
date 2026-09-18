package com.dragonspeech.engine;

import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared targeting math for the form engines: strike raycasts, the
 * leitbinda homing cone, and kedjubinda chain jumps. Behavior reference:
 * EBW's homing spark (SpellHoming/EntityMagicProjectile seeking) and
 * chain lightning (jump range, diminishing arcs).
 */
public final class Strikes {

    /** How far a homing working will bend to find a mark near its path. */
    private static final double HOMING_RANGE = 24.0;
    /** cos(~45 degrees) - the half-angle of the homing seek cone. */
    private static final double HOMING_CONE_COSINE = 0.7;
    /** How far one chain jump can leap, mirroring EBW's chain lightning. */
    private static final double CHAIN_JUMP_RANGE = 6.0;

    private Strikes() {}

    /** Where a strike fired from `from` along `direction` lands: the first entity, else the first block face, else the point `range` out. */
    public record StrikeHit(Vec3 pos, Entity entity, BlockPos blockPos) {}

    public static StrikeHit ray(ServerPlayer caster, Vec3 from, Vec3 direction, double range) {
        Vec3 end = from.add(direction.normalize().scale(range));

        AABB searchBox = new AABB(from, end).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            caster, from, end, searchBox,
            candidate -> candidate != caster && !candidate.isSpectator() && candidate.isPickable(),
            range * range
        );
        if (entityHit != null) {
            Entity entity = entityHit.getEntity();
            return new StrikeHit(entity.position().add(0, entity.getBbHeight() * 0.5, 0), entity, null);
        }

        HitResult blockHit = caster.level().clip(new ClipContext(
            from, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster
        ));
        if (blockHit.getType() == HitResult.Type.BLOCK && blockHit instanceof BlockHitResult blockHitResult) {
            return new StrikeHit(blockHit.getLocation(), null, blockHitResult.getBlockPos());
        }

        return new StrikeHit(end, null, null);
    }

    /**
     * leitbinda: the nearest living mark roughly along `direction` from
     * `from`, or null if nothing is in the seek cone. Excludes anything
     * already in `exclude` so multi-bolt castings spread across marks.
     */
    public static LivingEntity seek(ServerPlayer caster, Vec3 from, Vec3 direction, Set<Entity> exclude) {
        Vec3 look = direction.normalize();
        LivingEntity best = null;
        double bestCosine = HOMING_CONE_COSINE;

        for (LivingEntity candidate : caster.level().getEntitiesOfClass(LivingEntity.class,
            new AABB(from, from).inflate(HOMING_RANGE),
            e -> e != caster && e.isAlive() && !e.isSpectator() && !exclude.contains(e))) {

            Vec3 toCandidate = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(from);
            double along = toCandidate.dot(look);
            if (along <= 0 || along > HOMING_RANGE) {
                continue;
            }
            double cosine = toCandidate.normalize().dot(look);
            if (cosine > bestCosine) {
                bestCosine = cosine;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * kedjubinda: leaps from `firstStruck` to up to `jumps` further living
     * marks, each within CHAIN_JUMP_RANGE of the last, striking each at
     * diminishing power and drawing a lightning-arc between them. Returns
     * how many extra marks were struck.
     */
    public static int chain(WorkingContext ctx, Entity firstStruck, int jumps) {
        if (firstStruck == null || jumps <= 0) {
            return 0;
        }
        ServerLevel level = ctx.level();
        Set<Entity> struck = new HashSet<>();
        struck.add(ctx.caster());
        struck.add(firstStruck);

        Entity from = firstStruck;
        float powerScale = 0.75f;
        int arcs = 0;

        for (int i = 0; i < jumps; i++) {
            Vec3 fromPos = from.position().add(0, from.getBbHeight() * 0.5, 0);
            List<LivingEntity> candidates = new ArrayList<>(ctx.livingWithin(fromPos, CHAIN_JUMP_RANGE));
            candidates.removeAll(struck);
            if (candidates.isEmpty()) {
                break;
            }

            LivingEntity next = candidates.get(0);
            Vec3 intendedNextPos = next.position().add(0, next.getBbHeight() * 0.5, 0);
            SpellCollisionManager.PathResult collision = SpellCollisionManager.resolvePath(
                ctx, fromPos, intendedNextPos, .08f, ctx.power() * powerScale, 6);
            Vec3 nextPos = collision.end();

            boolean hasLightning = ctx.elements().contains(Element.LIGHTNING);
            var bodyElements = ctx.elements().stream().filter(e -> e != Element.LIGHTNING).toList();
            // Lightning chains use one canonical authored body: the custom forked LIGHTNING renderer.
            // Woven non-lightning elements may still travel with it, but lightning itself is never doubled.
            if (!bodyElements.isEmpty()) {
                SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.ARC, bodyElements,
                    fromPos, nextPos, .045f, 0f, 6);
            }
            if (hasLightning) {
                SpellFx.arc(level, Element.LIGHTNING.color(), from, fromPos, nextPos, 6);
            } else if (bodyElements.isEmpty()) {
                SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.ARC, ctx.elements(),
                    fromPos, nextPos, .045f, 0f, 6);
            }
            ctx.impactFx(nextPos);
            arcs++;
            if (collision.blocked()) {
                // A surviving barrier or stronger crossing working ends the chain at that point.
                break;
            }

            ctx.hitEntity(next, powerScale * collision.powerScale());
            struck.add(next);
            from = next;
            powerScale *= 0.75f; // each leap carries less, exactly like EBW's chain lightning
        }
        return arcs;
    }

    /** The first woven element's colour, for arcs/beams that need a single tint. */
    public static int primaryColor(WorkingContext ctx) {
        return ctx.elements().isEmpty() ? 0xffffff : ctx.elements().get(0).color();
    }

    /** Drops a point straight down onto the first solid surface below it (used by rain and sigils). */
    public static Vec3 dropToGround(ServerLevel level, ServerPlayer caster, Vec3 from, double maxDrop) {
        HitResult hit = level.clip(new ClipContext(
            from, from.add(0, -maxDrop, 0), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster
        ));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : from.add(0, -maxDrop, 0);
    }
}
