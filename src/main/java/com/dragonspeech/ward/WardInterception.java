package com.dragonspeech.ward;

import com.dragonspeech.engine.Element;
import com.dragonspeech.mob.casting.MobWards;
import com.dragonspeech.mob.casting.Warded;
import net.minecraft.world.entity.LivingEntity;

public final class WardInterception {
    private static final ThreadLocal<Integer> ELEMENT_PAYLOAD_DEPTH = ThreadLocal.withInitial(() -> 0);
    private WardInterception() {}
    public static boolean isApplyingElementPayload(){ return ELEMENT_PAYLOAD_DEPTH.get()>0; }
    public static void runElementPayload(Runnable action){ ELEMENT_PAYLOAD_DEPTH.set(ELEMENT_PAYLOAD_DEPTH.get()+1); try{action.run();} finally{int d=ELEMENT_PAYLOAD_DEPTH.get()-1;if(d<=0)ELEMENT_PAYLOAD_DEPTH.remove();else ELEMENT_PAYLOAD_DEPTH.set(d);} }
    public static boolean blocks(LivingEntity target, WardType type, float pressure) {
        if(target==null||type==null)return false; float cost=Math.max(.5f,pressure);
        if(WardService.absorb(target,type,cost)>=cost-.0001f)return true;
        return target instanceof Warded w && MobWards.absorbSpecific(w.activeWards(),target,type,cost);
    }
    public static boolean blocksElement(LivingEntity target, Element element, float pressure){ return WardType.fromElement(element).map(t->blocks(target,t,pressure)).orElse(false); }
}
