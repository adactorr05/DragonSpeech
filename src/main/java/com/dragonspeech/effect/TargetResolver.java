package com.dragonspeech.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Minimal "what is the caster looking at" targeting - entities take
 * priority over blocks, matching how most Minecraft interactions work.
 *
 * Uses ProjectileUtil.getEntityHitResult, the same long-standing vanilla
 * utility arrows/tridents/fishing rods use for "what did this projectile
 * hit" - reused here since it's a well-established, stable pattern rather
 * than a hand-rolled raycast.
 */
public final class TargetResolver {

    private TargetResolver() {}

    /** Area scope ("umhverf" etc.): every living entity within radius of the caster, excluding the caster themself. Ordered nearest-first so cap-truncation keeps the closest targets. */
    public static List<EffectTarget> resolveArea(ServerPlayer player, float radius) {
        return player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                candidate -> candidate != player && !candidate.isSpectator())
            .stream()
            .sorted((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
            .<EffectTarget>map(EffectTarget.OfEntity::new)
            .toList();
    }

    /**
     * Living-only ray for direct life-force workings. Nonliving magical barrier entities are ignored,
     * but a real block still shortens the ray so this is not a through-walls targeting exploit.
     */
    public static List<EffectTarget> resolveLivingLookTargetIgnoringBarriers(ServerPlayer player, double maxReach) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 fullEnd = start.add(look.scale(maxReach));
        HitResult wall = player.level().clip(new ClipContext(start, fullEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : fullEnd;
        double reachSqr = start.distanceToSqr(end);
        var searchBox = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, start, end, searchBox,
            candidate -> candidate instanceof net.minecraft.world.entity.LivingEntity && !candidate.isSpectator() && candidate.isPickable(), reachSqr);
        return hit == null ? List.of() : List.of(new EffectTarget.OfEntity(hit.getEntity()));
    }

    public static List<EffectTarget> resolveLookTarget(ServerPlayer player, double maxReach) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = start.add(look.scale(maxReach));

        var searchBox = player.getBoundingBox().expandTowards(look.scale(maxReach)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            player, start, end, searchBox,
            candidate -> !candidate.isSpectator() && candidate.isPickable(),
            maxReach * maxReach
        );
        if (entityHit != null) {
            return List.of(new EffectTarget.OfEntity(entityHit.getEntity()));
        }

        HitResult blockHit = player.level().clip(new ClipContext(
            start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        ));
        if (blockHit.getType() == HitResult.Type.BLOCK && blockHit instanceof BlockHitResult blockHitResult) {
            return List.of(new EffectTarget.OfBlock(blockHitResult.getBlockPos()));
        }

        return List.of();
    }

    /**
     * The "marklaust" target: a free cast from the caster's eyes, along the
     * combined spoken direction words (or the gaze if none were spoken).
     * Handlers that accept DIRECTION resolve their own strike point from it.
     */
    public static EffectTarget resolveDirection(ServerPlayer player, Vec3 spokenDirection) {
        Vec3 direction = spokenDirection != null && spokenDirection.lengthSqr() > 0.0001
            ? spokenDirection.normalize()
            : player.getLookAngle();
        return new EffectTarget.OfDirection(player.getEyePosition(), direction);
    }

    public static float distanceTo(ServerPlayer player, EffectTarget target) {
        Vec3 targetPos = switch (target) {
            case EffectTarget.OfEntity(Entity entity) -> entity.position();
            case EffectTarget.OfBlock(BlockPos pos) -> Vec3.atCenterOf(pos);
            // A free cast leaves from the caster themself - distance-to-target
            // is zero; the handler's own range cap governs how far it reaches.
            case EffectTarget.OfDirection(Vec3 origin, Vec3 ignored) -> origin;
        };
        return (float) player.position().distanceTo(targetPos);
    }
}
