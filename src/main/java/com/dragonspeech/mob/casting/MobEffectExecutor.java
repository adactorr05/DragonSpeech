package com.dragonspeech.mob.casting;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.block.BlockType;
import com.dragonspeech.mob.SummonType;
import com.dragonspeech.spell.SpellComposition;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Actually carries out a mob's composed spell. Deliberately its own small,
 * hand-written dispatch table rather than reusing the real EffectHandler
 * classes in com.dragonspeech.effect - see MobSpellComposer's class comment
 * for why those (EffectInvocation is hard-wired to a ServerPlayer caster)
 * weren't reused. Only implements the effect ids SpellIntent currently
 * offers to mob AI, so the default branch below should be unreachable in
 * normal play - it's a safety net, not an expected path.
 *
 * sunder/hurl_block/pillar/shape_block (the four block-manipulation
 * effects deferred in the first pass) are implemented below by directly
 * mirroring the real, already-written SunderEffectHandler /
 * BlockThrowEffectHandler / PillarEffectHandler / ShapeBlockEffectHandler
 * in com.dragonspeech.effect line-for-line wherever their logic doesn't
 * actually depend on a ServerPlayer caster - level.destroyBlock(pos, true,
 * self), BlockState.getDestroySpeed(level, pos), BlockType.blockState(),
 * FallingBlockEntity.fall(level, pos, state), and the ClipContext raycast
 * shape are all copied from those proven call sites rather than
 * reconstructed from memory, which is the single biggest risk-reduction
 * available without a compiler on hand.
 *
 * VERSION-RISK NOTE (same spirit as DragonEntity's own note): written
 * against well-known 1.21.1 Mojmap API shapes without a decompiled jar on
 * hand to confirm every method name this session. Entity.hurtMarked (used
 * below to force a velocity sync to nearby clients after push/lift) has
 * been renamed at least once across recent versions (hasImpulse in some) -
 * if compilation fails on that one line specifically, that's the field to
 * check first. Everything else here (damage sources, MobEffectInstance,
 * SummonType.create, block placement) is long-stable vanilla API.
 */
public final class MobEffectExecutor {

    private MobEffectExecutor() {}

    public static void execute(SpellComposition composition, SpellcastingMob caster, LivingEntity target) {
        Word verb = composition.wordsOf(WordCategory.VERB).stream().findFirst().orElse(null);
        if (verb == null || verb.effectHandlerId().isEmpty()) {
            return;
        }
        String effectPath = verb.effectHandlerId().get().getPath();
        LivingEntity self = caster.asEntity();
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }

        com.dragonspeech.engine.Element directElement = switch (effectPath) {
            case "ignite" -> com.dragonspeech.engine.Element.FIRE;
            case "freeze" -> com.dragonspeech.engine.Element.ICE;
            case "shock" -> com.dragonspeech.engine.Element.LIGHTNING;
            case "poison" -> com.dragonspeech.engine.Element.POISON;
            default -> null;
        };
        if (target != null && directElement != null && com.dragonspeech.ward.WardInterception.blocksElement(
                target, directElement, 8f + Math.max(0f, composition.modifierMagnitudeSum()) * 4f)) return;

        boolean elementalPayload = target != null && composition.words().stream().anyMatch(w -> w.element().isPresent());
        if (elementalPayload) {
            for (Word word : composition.words()) {
                if (word.element().isPresent() && com.dragonspeech.ward.WardInterception.blocksElement(
                        target, word.element().get(), 8f + Math.max(0f, composition.modifierMagnitudeSum()) * 4f)) return;
            }
        }

        // FIX: "some of the direct spells... bypassed my wards (blindness,
        // nausea, fatigue)" per explicit direction - MagicWardGate was
        // only ever wired into CastRequestHandler, the PLAYER-cast
        // pipeline. This is the completely separate MOB-cast pipeline
        // (see this class's own doc on why the two are parallel, not
        // shared) - nothing here ever consulted a ward at all. Several
        // of the cases below (confuse, petrify, freeze's slowness,
        // poison) apply a MobEffectInstance DIRECTLY with no damage and
        // no hurt() call whatsoever, so even the damage-based mixin
        // could never have caught them either - this single check
        // upstream of the whole switch is what actually closes the gap,
        // the same way it already does for marka in the player pipeline.
        if (target != null && !elementalPayload && com.dragonspeech.ward.MagicWardGate.isBlocked(self, target, effectPath)) {
            return;
        }
        if (target instanceof com.dragonspeech.dragon.DragonEntity dragon) {
            if ((effectPath.equals("ignite") && dragon.isImmuneToElement(com.dragonspeech.engine.Element.FIRE))
                || (effectPath.equals("shock") && dragon.isImmuneToElement(com.dragonspeech.engine.Element.LIGHTNING))
                || (effectPath.equals("freeze") && dragon.isImmuneToElement(com.dragonspeech.engine.Element.ICE))) {
                return;
            }
        }

        Runnable applyEffect = () -> {
        switch (effectPath) {
            case "push" -> {
                if (target != null) {
                    Vec3 dir = target.position().subtract(self.position()).normalize();
                    // MUCH gentler than the original 1.2 base (see
                    // SpellIntent's doc) - this used to be strong enough
                    // to fling a target clean out of the caster's own
                    // fighting range, which is why push was pulled from
                    // mob AI entirely for a while. A light shove instead
                    // of a launch is what let it come back.
                    double power = 0.35 + magnitudeBonus(composition, 0.3f);
                    target.push(dir.x * power, Math.max(0.05, dir.y * 0.2) + 0.1, dir.z * power);
                    target.hurtMarked = true;
                }
            }
            case "shock" -> damage(self, target, 4f + magnitudeBonus(composition, 4f));
            case "ignite" -> {
                if (target != null) {
                    int seconds = 4 + Math.round(4 * Math.max(0f, composition.modifierMagnitudeSum()));
                    target.igniteForSeconds(seconds);
                    damage(self, target, 2f);
                }
            }
            case "freeze" -> {
                if (target != null) {
                    int ticks = 100 + Math.round(60 * Math.max(0f, composition.modifierMagnitudeSum()));
                    int amp = (int) Math.min(3, 1 + Math.max(0f, composition.modifierMagnitudeSum()));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp));
                    target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, amp));
                }
            }
            case "poison" -> {
                if (target != null) {
                    int ticks = 100 + Math.round(60 * Math.max(0f, composition.modifierMagnitudeSum()));
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, 0));
                }
            }
            case "danger_word" -> {
                if (target != null) {
                    var danger = composition.wordsOf(WordCategory.VERB).stream().map(w -> com.dragonspeech.danger.DangerWordType.fromTrueName(w.trueName()).orElse(null)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
                    if (danger != null && !com.dragonspeech.danger.DangerWordService.isNaturallyImmune(target)
                            && !com.dragonspeech.ward.WardInterception.blocks(target, danger.wardType(), danger.wardPressure())) {
                        com.dragonspeech.danger.DangerWordService.applyDirectDamage(self, target, com.dragonspeech.danger.DangerWordService.damageFor(danger, target));
                    }
                }
            }
            case "confuse" -> {
                if (target != null) {
                    int ticks = 100 + Math.round(60 * Math.max(0f, composition.modifierMagnitudeSum()));
                    target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.min(ticks, 60), 0));
                }
            }
            case "petrify" -> {
                if (target != null) {
                    int ticks = 60 + Math.round(40 * Math.max(0f, composition.modifierMagnitudeSum()));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9));
                    target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 3));
                }
            }
            case "heal" -> self.heal(6f + magnitudeBonus(composition, 6f));
            case "teleport" -> {
                Vec3 dest = target != null
                    ? target.position().subtract(target.getLookAngle().scale(2.0))
                    : self.position().add(self.getLookAngle().scale(6.0));
                self.teleportTo(dest.x, dest.y, dest.z);
            }
            case "lift" -> {
                if (target != null) {
                    target.push(0, 0.8 + Math.max(0f, composition.modifierMagnitudeSum()) * 0.4, 0);
                    target.hurtMarked = true;
                }
            }
            case "summon" -> summon(level, composition, self, target);
            case "wall" -> buildWall(level, self, target);
            case "sunder" -> sunder(level, self, target);
            case "hurl_block" -> hurlBlock(level, composition, self, target);
            case "pillar" -> pillar(level, composition, self);
            case "shape_block" -> shapeBlock(level, composition, self, target);
            default -> DragonSpeech.LOGGER.warn("[DragonSpeech] Spellcasting mob tried unhandled effect '{}'", effectPath);
        }
        };
        if (elementalPayload) com.dragonspeech.ward.WardInterception.runElementPayload(applyEffect); else applyEffect.run();
    }

    private static float magnitudeBonus(SpellComposition composition, float scale) {
        return Math.max(0f, composition.modifierMagnitudeSum()) * scale;
    }

    /**
     * indirectMagic(attacker, attacker) - NOT magic() - is the one that
     * actually attaches an attacking entity to the DamageSource.
     * damageSources().magic() (used here in an earlier pass, matched
     * against SunderEffectHandler/BlockThrowEffectHandler's own call for
     * environment-targeting effects) carries no entity at all, which
     * silently broke retaliation: LivingEntity.setLastHurtByMob() - what
     * HurtByTargetGoal actually watches - only fires when the damage
     * source has an attached entity. Fixed so a mob spell properly makes
     * its target fight back (and, as a side effect, look at its
     * attacker - MobKeepDistanceGoal's look-control only runs once
     * getTarget() is non-null).
     */
    private static void damage(LivingEntity self, LivingEntity target, float amount) {
        if (target == null) {
            return;
        }
        DamageSource source = self.damageSources().indirectMagic(self, self);
        target.hurt(source, amount);
    }

    private static void summon(ServerLevel level, SpellComposition composition, LivingEntity self, LivingEntity target) {
        Word noun = composition.wordsOf(WordCategory.NOUN_TARGET).stream()
            .filter(w -> w.summonType().isPresent())
            .findFirst()
            .orElse(null);
        SummonType summonType = noun != null ? noun.summonType().orElse(SummonType.SKELETON) : SummonType.SKELETON;

        Entity summoned = summonType.create(level);
        if (summoned == null) {
            return;
        }
        double x = self.getX() + (level.random.nextDouble() - 0.5) * 2.0;
        double z = self.getZ() + (level.random.nextDouble() - 0.5) * 2.0;
        summoned.moveTo(x, self.getY(), z, self.getYRot(), 0f);
        level.addFreshEntity(summoned);
        if (summoned instanceof Mob summonedMob && target != null) {
            summonedMob.setTarget(target);
        }
    }

    private static void buildWall(ServerLevel level, LivingEntity self, LivingEntity target) {
        Vec3 facing = target != null ? target.position().subtract(self.position()).normalize() : self.getLookAngle();
        BlockPos origin = self.blockPosition().offset((int) Math.round(facing.x * 2), 0, (int) Math.round(facing.z * 2));
        boolean alongX = Math.abs(facing.x) <= Math.abs(facing.z);
        int stepX = alongX ? 1 : 0;
        int stepZ = alongX ? 0 : 1;
        for (int h = 0; h < 3; h++) {
            for (int w = -1; w <= 1; w++) {
                BlockPos pos = origin.offset(stepX * w, h, stepZ * w);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
    }

    private static final float MAX_SUNDER_HARDNESS = 5.0f; // matches SunderEffectHandler's own cap - stone=1.5, ores~3, obsidian=50 (excluded)

    /**
     * Mirrors SunderEffectHandler exactly, just with a raycast standing in
     * for the real handler's scope-word-resolved EffectTarget.OfBlock
     * (mobs have no equivalent of a player's crosshair/scope word to
     * resolve one from) - same ClipContext shape TargetResolver already
     * uses elsewhere in this codebase.
     */
    private static void sunder(ServerLevel level, LivingEntity self, LivingEntity target) {
        BlockPos pos = raycastBlock(level, self, target);
        if (pos == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (state.isAir() || hardness < 0f || hardness > MAX_SUNDER_HARDNESS) {
            return; // unbreakable, or beyond this word's strength - same cap the real handler enforces
        }
        level.destroyBlock(pos, true, self);
    }

    /**
     * Mirrors BlockThrowEffectHandler: direct damage (no real projectile
     * flight/collision physics - see that class's own doc for why) plus a
     * best-effort FallingBlockEntity thrown toward the target as a purely
     * cosmetic flourish, wrapped so a spawn failure there can never
     * undo the damage that already landed.
     */
    private static void hurlBlock(ServerLevel level, SpellComposition composition, LivingEntity self, LivingEntity target) {
        if (target == null) {
            return;
        }
        BlockType material = namedBlock(composition).orElse(BlockType.STONE);
        float weight = switch (material) {
            case DIRT, SAND -> 0.8f;
            case WOOD -> 1.0f;
            case NETHERRACK -> 1.1f;
            case STONE -> 1.3f;
            case END_STONE -> 1.4f;
        };
        damage(self, target, (4f + magnitudeBonus(composition, 4f)) * weight);

        try {
            FallingBlockEntity debris = FallingBlockEntity.fall(level, target.blockPosition().above(3), material.blockState());
            debris.setDeltaMovement(0, -0.2, 0);
        } catch (Exception ignored) {
            // visual flourish only - the hit already landed, see class doc
        }
    }

    private static final int MAX_PILLAR_HEIGHT = 8; // matches PillarEffectHandler's own cap

    /** Mirrors PillarEffectHandler: stack real blocks beneath self and ride them up, stopping the instant something solid is in the way rather than punching through it. */
    private static void pillar(ServerLevel level, SpellComposition composition, LivingEntity self) {
        BlockType material = namedBlock(composition).orElse(BlockType.STONE);
        int requestedHeight = Math.round(3f + Math.max(0f, composition.modifierMagnitudeSum()) * 3f);
        int height = Math.max(1, Math.min(requestedHeight, MAX_PILLAR_HEIGHT));

        BlockPos feet = self.blockPosition();
        BlockState state = material.blockState();

        int built = 0;
        for (int i = 0; i < height; i++) {
            BlockPos pos = feet.above(i);
            BlockState current = level.getBlockState(pos);
            if (!current.canBeReplaced() && !current.isAir()) {
                break;
            }
            level.setBlockAndUpdate(pos, state);
            built++;
        }
        if (built == 0) {
            return;
        }
        self.setPos(self.getX(), feet.getY() + built, self.getZ());
        self.fallDistance = 0f;
    }

    /** Mirrors ShapeBlockEffectHandler: swap exactly one looked-at block for the named material, same hardness cap as sunder (this is a material swap, not terraforming). */
    private static void shapeBlock(ServerLevel level, SpellComposition composition, LivingEntity self, LivingEntity target) {
        BlockPos pos = raycastBlock(level, self, target);
        if (pos == null) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        float hardness = current.getDestroySpeed(level, pos);
        if (hardness < 0f || hardness > MAX_SUNDER_HARDNESS) {
            return;
        }
        BlockType material = namedBlock(composition).orElse(BlockType.STONE);
        level.setBlockAndUpdate(pos, material.blockState());
    }

    /** Same ClipContext shape TargetResolver.java already uses to resolve a player's looked-at block. */
    private static BlockPos raycastBlock(ServerLevel level, LivingEntity self, LivingEntity target) {
        Vec3 from = self.getEyePosition();
        Vec3 dir = target != null ? target.position().subtract(from).normalize() : self.getLookAngle();
        Vec3 to = from.add(dir.scale(6.0));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, self));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private static Optional<BlockType> namedBlock(SpellComposition composition) {
        return composition.words().stream()
            .map(Word::blockType)
            .flatMap(Optional::stream)
            .findFirst();
    }
}
