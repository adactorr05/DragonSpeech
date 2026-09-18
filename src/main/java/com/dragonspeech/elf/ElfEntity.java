package com.dragonspeech.elf;

import com.dragonspeech.mob.casting.MobKeepDistanceGoal;
import com.dragonspeech.mob.casting.MobPowerTier;
import com.dragonspeech.mob.casting.MobSpellCastGoal;
import com.dragonspeech.mob.casting.MobWordPools;
import com.dragonspeech.mob.casting.SpellIntent;
import com.dragonspeech.mob.casting.SpellcastingMobEntity;
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

/**
 * The plain Elf: neutral, only fights back when provoked (HurtByTargetGoal),
 * prefers to disable (CROWD_CONTROL) before it hurts (OFFENSE), mends
 * itself (SELF_HEAL) if nothing else is affordable, and - now that it also
 * knows "sula" (pillar) - can launch itself away (MOBILITY) as a last
 * resort, which MobSpellCastGoal automatically tries first once health
 * drops critically low. See the priority order passed to MobSpellCastGoal
 * below. MobKeepDistanceGoal keeps it at caster's range instead of walking
 * into melee once it has a target.
 *
 * Trading (word-for-emerald, per the design brief) is handled entirely by
 * the shared SpellcastingMobEntity base class - see MobTradeOffers - so
 * there's nothing race-specific to wire up here for it.
 */
public class ElfEntity extends SpellcastingMobEntity {

    public ElfEntity(EntityType<? extends ElfEntity> type, Level level) {
        super(type, level, MobPowerTier.ADEPT);
        applyStartingVocabulary(MobWordPools.ELF);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MobSpellCastGoal(this, List.of(
            SpellIntent.CROWD_CONTROL, SpellIntent.OFFENSE, SpellIntent.SELF_HEAL, SpellIntent.MOBILITY
        ), 12.0));
        this.goalSelector.addGoal(2, new MobKeepDistanceGoal(this, 5.0, 9.0, 1.0));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        // MobDetectionCastGoal deliberately NOT registered here - per
        // explicit direction, stamina/mark detection is a player-facing
        // spell, not something mobs should cast on their own.
        // Elves are now automatically hostile toward Shades - not just when
        // attacked - per explicit direction, so this goes ahead of the
        // reactive HurtByTargetGoal rather than replacing it.
        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, com.dragonspeech.shade.ShadeEntity.class, true));
        // "Elves, Elder elves, and Human mages should attack hostile
        // mobs (like zombies, skeletons, etc.)" per explicit direction -
        // Monster is vanilla's own base class for exactly that group, so
        // this covers all of them (and any future ones) without a
        // hand-maintained list.
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.monster.Monster.class, true));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }
}
