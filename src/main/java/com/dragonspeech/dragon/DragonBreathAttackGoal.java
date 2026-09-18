package com.dragonspeech.dragon;

import com.dragonspeech.fx.SpellFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The dragon's ranged breath attack. Uses DragonColor#breathElement()
 * for flavor (particle color, status effect) but applies damage directly
 * via LivingEntity#hurt with a mob attack damage source, rather than
 * routing through Element#hitEntity - that method's signature requires
 * a ServerPlayer caster (it's built for player-cast spells; see its
 * javadoc), and a dragon breathing fire is not a player casting a
 * working. This keeps the visual/status flavor consistent with the
 * spellcasting engine without pretending a dragon is a spellcaster.
 */
public class DragonBreathAttackGoal extends Goal {

    private final DragonEntity dragon;
    private final double range;
    private final int cooldownTicks;
    private int cooldown = 0;
    private int windupTicks = 0;

    private static final int WINDUP_TICKS = 15;
    private static final int STATUS_DURATION_TICKS = 100;

    public DragonBreathAttackGoal(DragonEntity dragon, double range, int cooldownTicks) {
        this.dragon = dragon;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
        }
        LivingEntity target = dragon.getTarget();
        return target != null && target.isAlive() && cooldown <= 0
            && dragon.distanceToSqr(target) <= range * range && dragon.hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = dragon.getTarget();
        return target != null && target.isAlive() && windupTicks > 0 && dragon.hasLineOfSight(target);
    }

    @Override
    public void start() {
        windupTicks = WINDUP_TICKS;
    }

    @Override
    public void stop() {
        windupTicks = 0;
        cooldown = cooldownTicks;
    }

    /**
     * "I want it to come out of the mouth of the dragon. (when it fires
     * from where it is currently, it hurts itself too)" - getEyePosition()
     * alone is near the top of the skull, not the snout/jaw, which
     * matches both symptoms: visually wrong (the green-circle marker in
     * the bug report is nowhere near the mouth), and likely the actual
     * cause of the self-damage too - fire ignition that close to the
     * dragon's own head/body can catch the dragon itself. Pushes the
     * origin forward (along the look direction) and down from eye level
     * toward where the jaw actually is, scaled to the entity's current
     * size (getBbWidth()/getBbHeight() already reflect DragonAgeStage's
     * scale) so a hatchling doesn't breathe fire from a foot in front of
     * its face.
     */
    /**
     * "Still comes from above the head somewhere." getEyePosition() is
     * a generic vanilla hitbox calculation (roughly eye-height-to-total-
     * height ratio) that has no idea this model has a long neck - it
     * doesn't correlate well with where the head actually sits. Still
     * an empirical offset, not derived from exact geometry (I don't
     * have a live render to verify against), but anchoring from the
     * entity's feet with a direct height fraction is a more decisive,
     * more directly-controllable correction than nudging down from a
     * hitbox-based eye position that was already the wrong reference
     * point.
     */
    private net.minecraft.world.phys.Vec3 mouthPosition() {
        var look = dragon.getLookAngle();
        double forwardOffset = dragon.getBbWidth() * 1.1;
        double heightFraction = dragon.getBbHeight() * 0.4;
        return dragon.position().add(0, heightFraction, 0).add(look.scale(forwardOffset));
    }

    @Override
    public void tick() {
        LivingEntity target = dragon.getTarget();
        if (target == null) {
            return;
        }
        dragon.getLookControl().setLookAt(target, 30f, 30f);

        if (windupTicks > 0) {
            windupTicks--;
            if (dragon.level() instanceof ServerLevel serverLevel) {
                var color = dragon.color();
                SpellFx.trail(serverLevel, color.breathElement().trailParticle(), color.breathElement().color(),
                    color.breathElement().fadeColor(), mouthPosition(), target.getEyePosition(), 0.4);
            }
            if (windupTicks == 0) {
                breathe(target);
            }
        }
    }

    private void breathe(LivingEntity target) {
        if (!(dragon.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // FIX: ageStage().damageMultiplier() (the old discrete 5-tier
        // enum) no longer exists on the new DML-based entity - same
        // adaptation as DragonEntity.maxDragonStamina() itself,
        // getAgeScale() (this entity's own continuous 0.33-1.0 growth
        // value) standing in as a reasonable, consistent scaling factor.
        float baseDamage = 12f * dragon.getAgeScale();
        target.hurt(dragon.damageSources().mobAttack(dragon), baseDamage);
        applyBreathFlavor(target);
        SpellFx.burst(serverLevel, dragon.color().breathElement().trailParticle(), dragon.color().breathElement().color(),
            dragon.color().breathElement().fadeColor(), target.position(), 20, 0.3);
    }

    /** Mirrors (but does not call) Element#hitEntity's status-effect flavor per color, since that method is player-cast-only. Keep this in sync by hand if Element's flavor ever changes. */
    private void applyBreathFlavor(LivingEntity target) {
        switch (dragon.color().breathElement()) {
            case FIRE -> target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), STATUS_DURATION_TICKS));
            case ICE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STATUS_DURATION_TICKS, 2));
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), STATUS_DURATION_TICKS));
            }
            case LIGHTNING -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STATUS_DURATION_TICKS / 2, 1));
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, STATUS_DURATION_TICKS, 1));
            case SHADOW -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, STATUS_DURATION_TICKS, 0));
            case DEATH -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, STATUS_DURATION_TICKS / 2, 1));
            default -> { /* other elements not currently assigned to any DragonColor */ }
        }
    }
}
