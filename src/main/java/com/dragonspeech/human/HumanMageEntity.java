package com.dragonspeech.human;

import com.dragonspeech.mob.casting.MobKeepDistanceGoal;
import com.dragonspeech.mob.casting.MobPowerTier;
import com.dragonspeech.mob.casting.MobSpellCastGoal;
import com.dragonspeech.mob.casting.MobWordPools;
import com.dragonspeech.mob.casting.SpellIntent;
import com.dragonspeech.mob.casting.SpellcastingMobEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * "Not as powerful as elves, but can also cast magic (some can be hostile,
 * some neutral)" per the design brief. Mechanically: MobPowerTier.APPRENTICE
 * (smallest energy pool, longest cooldown of the four mobs), and a
 * per-spawn coin flip for hostile vs. neutral that also picks which starting
 * word pool it's born with (the hostile pool adds a few Death/dark words a
 * neutral mage wouldn't touch - see MobWordPools).
 *
 * The hostile/neutral choice is made inside registerGoals() (called by
 * Mob's own constructor, before this class's constructor body runs) rather
 * than in the constructor body itself, since the goal lists genuinely
 * differ between the two and Goal wiring has to happen at construction
 * time. readAdditionalSaveData reconciles that wiring if a saved value
 * disagrees with the fresh random roll (relevant only when loading an
 * existing save, since MobVocabulary itself is always persisted directly
 * and doesn't depend on this flag after first spawn).
 */
public class HumanMageEntity extends SpellcastingMobEntity {

    private static final float HOSTILE_CHANCE = 0.3f;

    private boolean hostile;
    private Goal neutralRetaliateGoal;
    private Goal hostileTargetGoal;

    public HumanMageEntity(EntityType<? extends HumanMageEntity> type, Level level) {
        super(type, level, MobPowerTier.APPRENTICE);
        applyStartingVocabulary(hostile ? MobWordPools.HUMAN_MAGE_HOSTILE : MobWordPools.HUMAN_MAGE_NEUTRAL);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 16.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.FOLLOW_RANGE, 28.0)
            .add(Attributes.ATTACK_DAMAGE, 1.5);
    }

    public boolean isHostile() {
        return hostile;
    }

    /** A mage actively attacking the player has no business opening a trade menu - see SpellcastingMobEntity.mobInteract. */
    @Override
    protected boolean canTrade() {
        return !hostile;
    }

    @Override
    protected void registerGoals() {
        this.hostile = this.getRandom().nextFloat() < HOSTILE_CHANCE;

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MobSpellCastGoal(this, List.of(
            SpellIntent.OFFENSE, SpellIntent.CROWD_CONTROL, SpellIntent.MOBILITY
        ), 11.0));
        this.goalSelector.addGoal(2, new MobKeepDistanceGoal(this, 4.0, 8.0, 1.0));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        // MobDetectionCastGoal deliberately NOT registered here - per
        // explicit direction, stamina/mark detection is a player-facing
        // spell, not something mobs should cast on their own.

        // Automatically hostile toward Shades regardless of the neutral/
        // hostile roll below - per explicit direction, this applies to
        // EVERY Human Mage, not just the hostile-rolled ones.
        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, com.dragonspeech.shade.ShadeEntity.class, true));
        this.neutralRetaliateGoal = new HurtByTargetGoal(this);
        this.hostileTargetGoal = new NearestAttackableTargetGoal<>(this, Player.class, true);
        this.targetSelector.addGoal(1, this.hostile ? this.hostileTargetGoal : this.neutralRetaliateGoal);
        // "Elves, Elder elves, and Human mages should attack hostile
        // mobs (like zombies, skeletons, etc.)" per explicit direction -
        // applies to every Human Mage regardless of the hostile/neutral
        // roll above, same as the Shade-targeting goal already does.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.monster.Monster.class, true));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("dragonspeech_hostile", hostile);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("dragonspeech_hostile")) {
            return;
        }
        boolean savedHostile = tag.getBoolean("dragonspeech_hostile");
        if (savedHostile != this.hostile) {
            this.targetSelector.removeGoal(this.hostile ? this.hostileTargetGoal : this.neutralRetaliateGoal);
            this.targetSelector.addGoal(1, savedHostile ? this.hostileTargetGoal : this.neutralRetaliateGoal);
            this.hostile = savedHostile;
        }
    }
}
