package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.danger.DangerWordService;
import com.dragonspeech.danger.DangerWordType;
import com.dragonspeech.ward.WardInterception;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;

public final class DangerWordEffectHandler implements EffectHandler {
    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(1,40f,24f, Set.of(TargetKind.ENTITY));
    @Override public ResourceLocation id(){return DragonSpeech.id("danger_word");}
    @Override public EffectHandlerCaps caps(){return CAPS;}
    @Override public float estimateBaseMagnitude(EffectInvocation i){var t=resolve(i);return t==null?20f:16f+t.ordinal()*5f;}
    @Override public EffectResult apply(EffectInvocation i){
        var bad=CAPS.validateTargets(i); if(bad.isPresent())return EffectResult.failure(bad.get());
        var w=resolve(i); if(w==null)return EffectResult.failure("The dangerous wording has no shape the language recognizes.");
        if(i.targets().isEmpty() || !(i.targets().get(0) instanceof EffectTarget.OfEntity(Entity e)) || !(e instanceof LivingEntity target)) return EffectResult.failure("There is no living force there for the word to seize.");
        if(DangerWordService.isNaturallyImmune(target))return EffectResult.success(0f,target.getName().getString()+" is naturally beyond the reach of that word.");
        if(WardInterception.blocks(target,w.wardType(),w.wardPressure()))return EffectResult.success(0f,"A ward keyed to that exact word catches it before it can touch life.");
        float damage=DangerWordService.damageFor(w,target);
        if(!DangerWordService.applyDirectDamage(i.caster(),target,damage))return EffectResult.failure("The word reaches for life, but finds no purchase.");
        return EffectResult.success(damage,"The word strikes life directly, tearing away "+Math.round(damage*10f)/10f+" health.");
    }
    private static DangerWordType resolve(EffectInvocation i){return i.composition().wordsOf(WordCategory.VERB).stream().map(w->DangerWordType.fromTrueName(w.trueName()).orElse(null)).filter(java.util.Objects::nonNull).findFirst().orElse(null);}
}
