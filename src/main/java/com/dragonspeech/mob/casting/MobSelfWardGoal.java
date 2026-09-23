package com.dragonspeech.mob.casting;
import com.dragonspeech.ward.WardType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.*;
import java.util.function.Supplier;
public class MobSelfWardGoal extends Goal {
 private final Mob mob;private final Warded warded;private final MobPowerTier tier;private final Supplier<List<WardType>> known;private int cooldown;
 public MobSelfWardGoal(Mob m,Warded w,MobPowerTier t,List<WardType> k){this(m,w,t,()->k);} public MobSelfWardGoal(Mob m,Warded w,MobPowerTier t,Supplier<List<WardType>> k){mob=m;warded=w;tier=t;known=k;cooldown=m.getRandom().nextInt(200);setFlags(EnumSet.noneOf(Goal.Flag.class));}
 private List<WardType> known(){List<WardType> l=known.get();return l==null?List.of():l;}
 @Override public boolean canUse(){if(cooldown>0){cooldown--;return false;}return known().stream().anyMatch(t->!warded.activeWards().containsKey(t));}
 @Override public boolean canContinueToUse(){return false;}
 @Override public void start(){known().stream().filter(t->!warded.activeWards().containsKey(t)).findFirst().ifPresent(t->warded.activeWards().put(t,new MobWards.WardInstance(t,MobWards.durabilityFor(tier))));cooldown=400+mob.getRandom().nextInt(400);}
}
