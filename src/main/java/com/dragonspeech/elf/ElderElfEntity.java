package com.dragonspeech.elf;

import com.dragonspeech.mob.casting.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * "Elders will have more stamina and be able to use more spells" per the
 * design brief - mechanically that's MobPowerTier.ELDER (bigger energy pool,
 * shorter cooldown than ADEPT) plus MobWordPools.ELDER_ELF, which is the
 * plain Elf's whole pool PLUS every elven-trial-precise upgrade of it.
 *
 * Also implements Warded: rolls a starting ward set at spawn - ONLY from
 * ward types matching words it actually knows (verja/eldverja/hoggverja/
 * fallverja), guaranteed melee plus usually 1 more - and can re-raise
 * any depleted ward mid-fight via MobSelfWardGoal. See MobWards for the
 * fuller reasoning (this replaced an earlier version that ignored
 * vocabulary entirely and had no real durability concept).
 *
 * IMPORTANT: activeWards is populated in the CONSTRUCTOR BODY, after
 * applyStartingVocabulary() runs - not as a field initializer. Field
 * initializers execute before any of the constructor body's own
 * statements, so rolling wards from vocabulary at that point would have
 * seen an empty vocabulary every time.
 *
 * Now also automatically hostile toward Shades (not just when attacked)
 * per explicit direction - see the targetSelector priority 0 entry below.
 */
public class ElderElfEntity extends SpellcastingMobEntity implements Warded {

    private static final List<MobWards.WardType> KNOWN_WARD_TYPES = List.of(
        MobWards.WardType.PROJECTILE, MobWards.WardType.FIRE, MobWards.WardType.MELEE, MobWards.WardType.FALL
    ); // matches verja/eldverja/hoggverja/fallverja, already in MobWordPools.ELDER_ELF

    private final Map<MobWards.WardType, MobWards.WardInstance> activeWards;

    public ElderElfEntity(EntityType<? extends ElderElfEntity> type, Level level) {
        super(type, level, MobPowerTier.ELDER);
        applyStartingVocabulary(MobWordPools.ELDER_ELF);
        this.activeWards = MobWards.rollStartingWards(this.getRandom(), MobPowerTier.ELDER, this.vocabulary());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    public Map<MobWards.WardType, MobWards.WardInstance> activeWards() {
        return activeWards;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        MobWards.WardResult result = MobWards.applyWards(activeWards, this, source, amount);
        if (result.blocked()) {
            // "Stops the damage from hitting you vs how it hits but
            // nullifies" per explicit direction - no super.hurt() call
            // at all means no hurt sound, no red flash, nothing but the
            // ward's own block sound/particles from applyWards above.
            return false;
        }
        return super.hurt(source, result.remainingDamage());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MobSpellCastGoal(this, List.of(
            SpellIntent.CROWD_CONTROL, SpellIntent.OFFENSE, SpellIntent.SELF_HEAL, SpellIntent.MOBILITY
        ), 13.0));
        this.goalSelector.addGoal(2, new MobKeepDistanceGoal(this, 6.0, 10.0, 1.0));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(6, new MobSelfWardGoal(this, this, MobPowerTier.ELDER, KNOWN_WARD_TYPES));
        // MobDetectionCastGoal deliberately NOT registered here - per
        // explicit direction, stamina/mark detection is a player-facing
        // spell, not something mobs should cast on their own.

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, com.dragonspeech.shade.ShadeEntity.class, true));
        // "Elves, Elder elves, and Human mages should attack hostile
        // mobs (like zombies, skeletons, etc.)" per explicit direction.
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.monster.Monster.class, true));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }
}
