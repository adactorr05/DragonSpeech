package com.dragonspeech.mob.casting;

import com.dragonspeech.ward.WardService;
import com.dragonspeech.ward.WardType;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import java.util.*;

public final class MobWards {
    private static final String NBT_LIST="dragonspeech_active_wards";
    private MobWards(){}
    public static final class WardInstance {
        private final WardType type; private final float maxDurability; private float durability;
        public WardInstance(WardType t,float max){this(t,max,max);} public WardInstance(WardType t,float max,float cur){type=t;maxDurability=Math.max(1,max);durability=Math.max(0,Math.min(maxDurability,cur));}
        public WardType type(){return type;} public float durability(){return durability;} public float maxDurability(){return maxDurability;}
        private void drain(float a){durability=Math.max(0,durability-Math.max(0,a));}
    }
    public static float durabilityFor(MobPowerTier t){return switch(t){case APPRENTICE->20f;case ADEPT->30f;case ELDER->50f;case CATASTROPHIC->80f;};}
    public static WardType wardFor(String n){return switch(n){case "hoggverja"->WardType.MELEE;case "verja"->WardType.PROJECTILE;case "eldverja"->WardType.FIRE;case "sprengverja"->WardType.EXPLOSION;case "seidverja"->WardType.MAGIC;case "fallverja"->WardType.FALL;default->null;};}
    public static List<WardType> eligibleWards(MobVocabulary v){List<WardType> out=new ArrayList<>();for(ResourceLocation id:v.words()){WardType t=wardFor(id.getPath());if(t!=null&&!out.contains(t))out.add(t);}return out;}
    public static Map<WardType,WardInstance> rollStartingWards(RandomSource r,MobPowerTier tier,MobVocabulary v){return rollStartingWards(r,tier,v,List.of());}
    public static Map<WardType,WardInstance> rollStartingWards(RandomSource r,MobPowerTier tier,MobVocabulary v,List<WardType> extra){
        Map<WardType,WardInstance> out=new EnumMap<>(WardType.class);List<WardType> eligible=new ArrayList<>(eligibleWards(v));for(WardType t:extra)if(!eligible.contains(t))eligible.add(t);if(eligible.isEmpty())return out;
        float d=durabilityFor(tier);if(eligible.contains(WardType.MELEE))out.put(WardType.MELEE,new WardInstance(WardType.MELEE,d));
        List<WardType> rem=new ArrayList<>(eligible);rem.remove(WardType.MELEE);Collections.shuffle(rem,new Random(r.nextLong()));
        if(!rem.isEmpty()&&r.nextFloat()<.55f){WardType t=rem.get(0);out.put(t,new WardInstance(t,d));}
        if(rem.size()>1&&r.nextFloat()<.15f){WardType t=rem.get(1);out.put(t,new WardInstance(t,d));}return out;
    }
    public record WardResult(float remainingDamage,boolean blocked){}
    public static boolean absorbSpecific(Map<WardType,WardInstance> wards,LivingEntity self,WardType type,float amount){
        WardInstance w=wards.get(type);if(w==null)return false;float cost=Math.max(.5f,amount);
        // Innate NPC wards are reserve wards: insufficient reserve collapses but does not block the attack.
        if(w.durability()+.0001f<cost){w.drain(w.durability());wards.remove(type);breakFx(self);return false;}
        w.drain(cost);WardService.playBlockFeedback(self,type);if(w.durability()<=0){wards.remove(type);breakFx(self);}return true;
    }
    private static void breakFx(LivingEntity self){if(self.level() instanceof ServerLevel level){level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,self.getX(),self.getEyeY(),self.getZ(),15,.4,.4,.4,.05);level.playSound(null,self.blockPosition(),net.minecraft.sounds.SoundEvents.SHIELD_BREAK,net.minecraft.sounds.SoundSource.NEUTRAL,.7f,1f);}}
    public static WardResult applyWards(Map<WardType,WardInstance> wards,LivingEntity self,DamageSource source,float amount){
        if(wards.isEmpty()||com.dragonspeech.danger.DangerWordService.isApplyingDirectDamage())return new WardResult(amount,false);
        boolean fire=source.is(DamageTypeTags.IS_FIRE), explosion=source.is(DamageTypeTags.IS_EXPLOSION), fall=source.is(DamageTypeTags.IS_FALL);
        boolean magic=!com.dragonspeech.ward.WardInterception.isApplyingElementPayload()&&(source.is(DamageTypes.MAGIC)||source.is(DamageTypes.INDIRECT_MAGIC));
        boolean projectile=source.getDirectEntity() instanceof Projectile; WardType t=null;
        if(wards.containsKey(WardType.FIRE)&&fire)t=WardType.FIRE;else if(wards.containsKey(WardType.EXPLOSION)&&explosion)t=WardType.EXPLOSION;else if(wards.containsKey(WardType.FALL)&&fall)t=WardType.FALL;else if(wards.containsKey(WardType.MAGIC)&&magic)t=WardType.MAGIC;else if(wards.containsKey(WardType.PROJECTILE)&&projectile)t=WardType.PROJECTILE;else if(wards.containsKey(WardType.MELEE)&&source.getEntity() instanceof LivingEntity&&!fire&&!explosion&&!magic&&!projectile)t=WardType.MELEE;
        if(t==null)return new WardResult(amount,false);if(source.getEntity() instanceof WardLearner learner&&learner.getLastCastVerb()!=null)learner.rememberBlocked(self.getUUID(),learner.getLastCastVerb());
        boolean blocked=absorbSpecific(wards,self,t,amount);if(blocked&&t==WardType.PROJECTILE)WardService.deflectProjectile(self,source.getDirectEntity());return blocked?new WardResult(0,true):new WardResult(amount,false);
    }
    public static void save(CompoundTag tag,Map<WardType,WardInstance> wards){ListTag list=new ListTag();for(WardInstance w:wards.values()){CompoundTag x=new CompoundTag();x.putString("type",w.type().name());x.putFloat("max",w.maxDurability());x.putFloat("remaining",w.durability());list.add(x);}tag.put(NBT_LIST,list);}
    public static void load(CompoundTag tag,Map<WardType,WardInstance> into){if(!tag.contains(NBT_LIST))return;into.clear();ListTag list=tag.getList(NBT_LIST,Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){CompoundTag x=list.getCompound(i);try{WardType t=WardType.valueOf(x.getString("type"));float m=Math.max(1,x.getFloat("max")),r=x.contains("remaining")?x.getFloat("remaining"):m;if(r>0)into.put(t,new WardInstance(t,m,r));}catch(IllegalArgumentException ignored){}}}
    public static void saveKnowledge(CompoundTag tag,String key,List<WardType> types){ListTag l=new ListTag();for(WardType t:types)l.add(StringTag.valueOf(t.name()));tag.put(key,l);}
    public static void loadKnowledge(CompoundTag tag,String key,List<WardType> into){if(!tag.contains(key))return;into.clear();ListTag l=tag.getList(key,Tag.TAG_STRING);for(int i=0;i<l.size();i++){try{WardType t=WardType.valueOf(l.getString(i));if(!into.contains(t))into.add(t);}catch(IllegalArgumentException ignored){}}}
}
