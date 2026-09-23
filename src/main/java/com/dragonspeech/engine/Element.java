package com.dragonspeech.engine;

import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The fixed, compiled set of elements a working can carry. This is the
 * same hard boundary effect_handler / summon_type / block_type enforce
 * elsewhere: a datapack word can TAG itself with one of these (the
 * "element" field on nouns like eldr/elding/is, or baked into a verb like
 * eldingkast), but it can never invent a new element or new element
 * behavior - what each element does on impact lives here, in code.
 *
 * Behavior reference: Electroblob's Wizardry's per-element spells
 * (firebolt/ice shard/lightning bolt/poison bomb/...) - their impact
 * effects are distilled into hitEntity/hitBlock so ANY form engine
 * (bolt, ray, rain, sigil...) produces the right elemental result.
 */
public enum Element implements StringRepresentable {

    FIRE(0xff6d2a, 0xffd211),
    LIGHTNING(0x4db8ff, 0xffffff),
    WIND(0xbcecff, 0xffffff),
    ICE(0xa4e5ff, 0xffffff),
    WATER(0x2f7bd6, 0x9adcff),
    POISON(0x54c936, 0x216b0e),
    FORCE(0xf2e28d, 0xffffff),
    EARTH(0x8a6a3b, 0xcbb27a),
    LIGHT(0xfff4b8, 0xffffff),
    SHADOW(0x352a4d, 0x0d0a17),
    DEATH(0x3d1a4f, 0x0e0413),
    LIFE(0x8bff8b, 0xd8ffd8),
    /** True absence / Void: not shadow or death, but a working that erodes presence itself. */
    VOID(0x190d24, 0x8f4fc7);

    public static final Codec<Element> CODEC = StringRepresentable.fromEnum(Element::values);

    private final int color;
    private final int fadeColor;

    Element(int color, int fadeColor) {
        this.color = color;
        this.fadeColor = fadeColor;
    }

    public int color() {
        return color;
    }

    public int fadeColor() {
        return fadeColor;
    }

    /** The sprite this element's trails and bursts are built from. */
    public SimpleParticleType trailParticle() {
        return switch (this) {
            case FIRE -> DragonSpeechParticles.MAGIC_FIRE;
            case LIGHTNING -> DragonSpeechParticles.SPARK;
            case WIND -> DragonSpeechParticles.PATH;
            case ICE -> DragonSpeechParticles.SNOW;
            case WATER -> DragonSpeechParticles.MAGIC_BUBBLE;
            case POISON, DEATH, SHADOW, VOID -> DragonSpeechParticles.DARK_MAGIC;
            case EARTH -> DragonSpeechParticles.DUST;
            case FORCE, LIGHT, LIFE -> DragonSpeechParticles.SPARKLE;
        };
    }

    /**
     * Applies this element's impact to a living target. `power` is the
     * already-clamped working power (roughly half-hearts of damage for the
     * damaging elements); form engines pass reduced power for area/pierce
     * forms so a wide working never out-damages a focused one for free.
     */
    public void hitEntity(ServerPlayer caster, Entity target, float power) {
        if (target instanceof com.dragonspeech.dragon.DragonEntity dragon && dragon.isImmuneToElement(this)) {
            return;
        }
        if (target instanceof LivingEntity living
                && com.dragonspeech.ward.WardInterception.blocksElement(living, this, Math.max(1f, power))) return;
        int durationTicks = Math.round(40 + power * 20);

        com.dragonspeech.ward.WardInterception.runElementPayload(() -> {
        switch (this) {
            case FIRE -> {
                damage(caster, target, power);
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), durationTicks));
            }
            case LIGHTNING -> damage(caster, target, power * 1.2f);
            case WIND -> {
                damage(caster, target, power * 0.25f);
                pushAway(caster, target, 1.0f + power * 0.14f);
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(20, durationTicks / 2), 0));
                }
            }
            case ICE -> {
                damage(caster, target, power * 0.8f);
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 2));
                    living.setTicksFrozen(Math.max(living.getTicksFrozen(), durationTicks));
                }
            }
            case WATER -> {
                damage(caster, target, power * 0.7f);
                target.clearFire();
                pushAway(caster, target, 0.4f + power * 0.05f);
            }
            case POISON -> {
                damage(caster, target, power * 0.4f);
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.POISON, durationTicks, 1));
                }
            }
            case FORCE -> {
                damage(caster, target, power * 0.6f);
                pushAway(caster, target, 0.8f + power * 0.1f);
            }
            case EARTH -> {
                damage(caster, target, power);
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks / 2, 1));
                }
            }
            case LIGHT -> {
                // Extra bite against the undead, like EBW's radiant spells.
                float amount = target instanceof LivingEntity living && living.isInvertedHealAndHarm() ? power * 1.5f : power * 0.8f;
                damage(caster, target, amount);
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0));
                }
            }
            case SHADOW -> {
                damage(caster, target, power * 0.8f);
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, durationTicks, 0));
                }
            }
            case DEATH -> {
                damage(caster, target, power * 0.7f);
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.WITHER, durationTicks / 2, 0));
                }
            }
            case LIFE -> {
                // Life doesn't wound - it mends. An aura of life heals; a
                // bolt of life is a thrown mending.
                if (target instanceof LivingEntity living) {
                    if (living.isInvertedHealAndHarm()) {
                        damage(caster, target, power); // undead are burned by life, mirroring vanilla
                    } else {
                        living.heal(power * 0.75f);
                    }
                }
            }
            case VOID -> {
                // Void is deliberately distinct from shadow/death. It does not poison or burn;
                // it erodes the target's presence and leaves perception/recovery impaired.
                damage(caster, target, power * 1.10f);
                if (target instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, Math.max(30, durationTicks), 0));
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Math.max(30, durationTicks / 2), 1));
                }
            }
        }
        });
    }

    /** Applies this element's mark to the world at a block position. */
    public void hitBlock(ServerLevel level, BlockPos pos, float power) {
        switch (this) {
            case FIRE -> {
                BlockPos above = level.getBlockState(pos).isAir() ? pos : pos.above();
                if (level.getBlockState(above).isAir() && level.getBlockState(above.below()).isSolid()) {
                    level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
                }
            }
            case ICE -> {
                if (level.getBlockState(pos).is(Blocks.WATER)) {
                    level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                } else {
                    BlockPos above = level.getBlockState(pos).isAir() ? pos : pos.above();
                    if (level.getBlockState(above).isAir() && level.getBlockState(above.below()).isSolid()
                        && Blocks.SNOW.defaultBlockState().canSurvive(level, above)) {
                        level.setBlockAndUpdate(above, Blocks.SNOW.defaultBlockState());
                    }
                }
            }
            case WATER -> {
                BlockPos above = level.getBlockState(pos).isAir() ? pos : pos.above();
                if (level.getBlockState(above).is(Blocks.FIRE)) {
                    level.removeBlock(above, false);
                }
            }
            default -> {
                // Lightning/poison/force/earth/light/shadow/death/life/void leave
                // no permanent block mark - only their fx and entity effects.
            }
        }
    }

    /** Trail from `from` to `to` in this element's colours. */
    public void trailFx(ServerLevel level, Vec3 from, Vec3 to) {
        SpellFx.trail(level, trailParticle(), color, fadeColor, from, to, 0.45);
    }

    /** Impact burst at a point in this element's colours. */
    public void impactFx(ServerLevel level, Vec3 pos) {
        SpellFx.burst(level, trailParticle(), color, fadeColor, pos, 12, 0.15);
        SpellFx.flash(level, color, pos);
    }

    private static void damage(ServerPlayer caster, Entity target, float amount) {
        if (amount <= 0) return;
        Level level = caster.level();
        target.hurt(level.damageSources().indirectMagic(caster, caster), amount);
    }

    private static void pushAway(ServerPlayer caster, Entity target, float strength) {
        Vec3 away = target.position().subtract(caster.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() < 0.0001) return;
        flat = flat.normalize().scale(strength);
        target.push(flat.x, 0.2, flat.z);
        target.hurtMarked = true;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
