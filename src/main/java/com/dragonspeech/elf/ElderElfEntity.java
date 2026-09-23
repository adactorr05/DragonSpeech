package com.dragonspeech.elf;

import com.dragonspeech.danger.*;
import com.dragonspeech.mob.casting.*;
import com.dragonspeech.ward.WardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.*;

public class ElderElfEntity extends SpellcastingMobEntity implements Warded {
 private static final String NBT_WARD_KNOWLEDGE="dragonspeech_known_ward_types";
 private static final List<WardType> BASE=List.of(WardType.PROJECTILE,WardType.FIRE,WardType.MELEE,WardType.FALL);
 private final List<WardType> knownWardTypes=new ArrayList<>(BASE);
 private final Map<WardType,MobWards.WardInstance> activeWards;
 public ElderElfEntity(EntityType<? extends ElderElfEntity> type,Level level){super(type,level,MobPowerTier.ELDER);applyStartingVocabulary(MobWordPools.ELDER_ELF);rollForbiddenKnowledge();activeWards=MobWards.rollStartingWards(getRandom(),MobPowerTier.ELDER,vocabulary(),knownWardTypes);}
 private void rollForbiddenKnowledge(){List<ResourceLocation> pool=new ArrayList<>(MobWordPools.DANGER_WORDS);Collections.shuffle(pool,new Random(getUUID().getMostSignificantBits()^0x4E41464E5645524AL));if(getRandom().nextFloat()<.12f&&!pool.isEmpty()){int n=getRandom().nextFloat()<.08f?2:1;for(int i=0;i<Math.min(n,pool.size());i++){ResourceLocation id=pool.get(i);vocabulary().learnAll(List.of(id));DangerWordType.fromTrueName(id.getPath()).ifPresent(t->addKnownWard(t.wardType()));}}if(getRandom().nextFloat()<.20f&&!pool.isEmpty()){DangerWordType.fromTrueName(pool.get(getRandom().nextInt(pool.size())).getPath()).ifPresent(t->addKnownWard(t.wardType()));if(getRandom().nextFloat()<.05f)DangerWordType.fromTrueName(pool.get(getRandom().nextInt(pool.size())).getPath()).ifPresent(t->addKnownWard(t.wardType()));}}
 private void addKnownWard(WardType t){if(!knownWardTypes.contains(t))knownWardTypes.add(t);}
 public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,40).add(Attributes.ATTACK_DAMAGE,3);}
 @Override public Map<WardType,MobWards.WardInstance> activeWards(){return activeWards;}
 @Override public boolean hurt(DamageSource source,float amount){if(DangerWordService.isApplyingDirectDamage())return super.hurt(source,amount);var r=MobWards.applyWards(activeWards,this,source,amount);return r.blocked()?false:super.hurt(source,r.remainingDamage());}
 @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);MobWards.save(tag,activeWards);MobWards.saveKnowledge(tag,NBT_WARD_KNOWLEDGE,knownWardTypes);}
 @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);MobWards.load(tag,activeWards);MobWards.loadKnowledge(tag,NBT_WARD_KNOWLEDGE,knownWardTypes);}
 @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new MobSpellCastGoal(this,List.of(SpellIntent.CROWD_CONTROL,SpellIntent.OFFENSE,SpellIntent.SELF_HEAL,SpellIntent.MOBILITY),13));goalSelector.addGoal(2,new MobKeepDistanceGoal(this,6,10,1));goalSelector.addGoal(3,new WaterAvoidingRandomStrollGoal(this,.8));goalSelector.addGoal(4,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(5,new RandomLookAroundGoal(this));goalSelector.addGoal(6,new MobSelfWardGoal(this,this,MobPowerTier.ELDER,()->knownWardTypes));targetSelector.addGoal(0,new NearestAttackableTargetGoal<>(this,com.dragonspeech.shade.ShadeEntity.class,true));targetSelector.addGoal(1,new NearestAttackableTargetGoal<>(this,net.minecraft.world.entity.monster.Monster.class,true));targetSelector.addGoal(2,new HurtByTargetGoal(this));}
}
