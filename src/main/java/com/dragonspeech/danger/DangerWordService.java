package com.dragonspeech.danger;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.warden.Warden;

public final class DangerWordService {
    public static final TagKey<EntityType<?>> NATURALLY_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, DragonSpeech.id("danger_word_immune"));
    private static final ThreadLocal<Integer> DIRECT_DAMAGE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private DangerWordService() {}
    public static boolean isApplyingDirectDamage(){ return DIRECT_DAMAGE_DEPTH.get() > 0; }
    public static boolean isNaturallyImmune(LivingEntity target) {
        if (target == null) return true;
        return target.getType().is(NATURALLY_IMMUNE) || target instanceof com.dragonspeech.dragon.DragonEntity
            || target instanceof EnderDragon || target instanceof Warden;
    }
    public static float damageFor(DangerWordType word, LivingEntity target) {
        float max = Math.max(1f,target.getMaxHealth());
        float scale = (float)Math.sqrt(20f/Math.max(20f,max));
        scale = Math.max(.20f,Math.min(1f,scale));
        return Math.max(1f,max*word.ordinaryHealthFraction()*scale);
    }
    public static boolean applyDirectDamage(LivingEntity caster, LivingEntity target, float amount) {
        if (caster==null||target==null||amount<=0) return false;
        DIRECT_DAMAGE_DEPTH.set(DIRECT_DAMAGE_DEPTH.get()+1);
        try { return target.hurt(caster.damageSources().indirectMagic(caster,caster), amount); }
        finally { int d=DIRECT_DAMAGE_DEPTH.get()-1; if(d<=0)DIRECT_DAMAGE_DEPTH.remove();else DIRECT_DAMAGE_DEPTH.set(d); }
    }
}
