package com.dragonspeech.client.fx;

import com.dragonspeech.engine.Element;
import com.dragonspeech.fx.SpellBodyVfxType;
import com.dragonspeech.network.SpellBodyVfxPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Geometry-first visual state for composed spells. The packet says only form + element mask +
 * geometry parameters: there are intentionally no named spell IDs here. FIRE+LANCE, ICE+LANCE,
 * and woven FIRE+LIGHTNING+LANCE all pass through the same renderer and remain sentence-derived.
 */
public final class SpellBodyVfxState {
    private static final class Effect {
        SpellBodyVfxType type; long id; int ownerId, elementMask; Vec3 a,b;
        float primary, secondary; int life,total,age; long seed;
        Effect(SpellBodyVfxPayload p) { update(p); total=life; age=0; }
        void update(SpellBodyVfxPayload p) {
            type=SpellBodyVfxType.byId(p.effectType()); id=p.effectId(); ownerId=p.ownerEntityId(); elementMask=p.elementMask();
            a=new Vec3(p.ax(),p.ay(),p.az()); b=new Vec3(p.bx(),p.by(),p.bz());
            primary=p.primary(); secondary=p.secondary(); life=Math.max(1,p.lifetime()); seed=p.seed();
            if(total<=0) total=life;
        }
    }

    private static final Map<Long,Effect> ACTIVE = new HashMap<>();
    private static boolean registered;
    private SpellBodyVfxState() {}

    public static void register() {
        if (registered) return;
        registered = true;
        WorldRenderEvents.AFTER_ENTITIES.register(SpellBodyVfxState::render);
    }

    public static void accept(SpellBodyVfxPayload payload) {
        if (SpellBodyVfxType.byId(payload.effectType()) == SpellBodyVfxType.REMOVE) {
            ACTIVE.remove(payload.effectId());
            return;
        }
        Effect old = ACTIVE.get(payload.effectId());
        if (old == null) ACTIVE.put(payload.effectId(), new Effect(payload));
        else old.update(payload);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { ACTIVE.clear(); return; }
        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Effect e = it.next().getValue();
            e.age++;
            if (--e.life <= 0) it.remove();
        }
    }

    private static void render(WorldRenderContext context) {
        MultiBufferSource consumers = context.consumers();
        if (consumers == null || context.world() == null) return;
        Vec3 camera = context.camera().getPosition();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(true);
        VertexConsumer vc = consumers.getBuffer(SpellBodyVfxRenderTypes.MAGIC);
        for (Effect e : ACTIVE.values()) {
            Vec3 focus = e.a.lerp(e.b, .5);
            if (focus.distanceToSqr(camera) > 180 * 180) continue;
            double time = e.age + partial;
            switch (e.type) {
                case BOLT -> renderProjectile(vc,camera,e,time,false,false);
                case ORB -> renderProjectile(vc,camera,e,time,true,false);
                case LANCE -> renderProjectile(vc,camera,e,time,false,true);
                case RAY -> renderBeam(vc,camera,visualHand(e),e.b,Math.max(.055f,e.primary),e,time);
                case TETHER -> renderTether(vc,camera,e,time);
                case CLAW -> renderClaws(vc,camera,e,time);
                case SPIRAL -> renderSpiral(vc,camera,e,time);
                case SHELL -> renderShell(vc,camera,e,time);
                case BURST, IMPACT -> renderBurst(vc,camera,e,time);
                case RING -> renderRing(vc,camera,e.a,Math.max(.4,e.primary),Math.max(.035f,e.secondary),e,time);
                case RAIN_STRIKE -> renderRainStrike(vc,camera,e,time);
                case CLOUD -> renderCloud(vc,camera,e,time);
                case AURA -> renderAura(vc,camera,e,time);
                case SIGIL -> renderSigil(vc,camera,e,time);
                case CONVERGENCE -> renderConvergence(vc,camera,e,time);
                case ORBIT -> renderOrbit(vc,camera,e,time);
                case REDIRECT -> renderRedirect(vc,camera,e,time);
                case WALL_RISE -> renderWallRise(vc,camera,e,time);
                case PILLAR_RISE -> renderPillarRise(vc,camera,e,time);
                case GALE -> renderGale(vc,camera,e,time);
                case UPDRAFT -> renderUpdraft(vc,camera,e,time);
                case ARC -> renderBeam(vc,camera,e.a,e.b,Math.max(.035f,e.primary),e,time);
                case CUTTING_RING -> renderCuttingRing(vc,camera,e,time);
                default -> {}
            }
        }
    }

    private static void renderProjectile(VertexConsumer vc, Vec3 cam, Effect e, double time, boolean orb, boolean lance) {
        double progress = Mth.clamp((e.age + (time-e.age)) / (double)Math.max(1,e.total), 0, 1);
        Vec3 start = visualHand(e);
        Vec3 p = start.lerp(e.b, progress);
        Vec3 dir = e.b.subtract(start);
        if (dir.lengthSqr() < 1e-8) dir = SpellRenderAnchors.forward(e.ownerId,new Vec3(0,0,1)); else dir = dir.normalize();
        float size = Math.max(.10f,e.primary);
        if (hasElement(e, Element.LIGHTNING) && !orb) {
            if (!lance) {
                // A lightning BOLT uses the same full-length jagged channel as kedjubinda's ARC.
                // This removes the old mismatch where the first leg was a moving little bolt and
                // every chain leg was a full forked strike.
                renderLightningBeam(vc, cam, start, e.b, e, time);
            } else {
                double length = Math.max(3.0, 4.0 + e.secondary * 1.8);
                Vec3 la = p.subtract(dir.scale(length * .72));
                Vec3 lb = p.add(dir.scale(length * .28));
                renderLightningBolt(vc, cam, la, lb, .045 + size * .08, 0,
                    e.seed ^ ((long)e.age / 2 * 31L));
            }
            if (elementCount(e) == 1) return;
        }
        if (hasElement(e, Element.VOID)) {
            renderVoidProjectile(vc, cam, p, dir, size, lance, orb, e, time);
            if (elementCount(e) == 1) return;
        }
        // Ice and earth are deliberately rendered as physical-looking spell bodies instead of
        // recolored energy. In woven spells their specialized body is kept and the generic
        // palette layers below add the other spoken element(s) rather than averaging them away.
        if (hasElement(e, Element.ICE)) {
            renderIceProjectile(vc, cam, p, dir, size, lance, orb, e, time);
            if (elementCount(e) == 1) return;
        }
        if (hasElement(e, Element.EARTH)) {
            renderEarthProjectile(vc, cam, p, dir, size, lance, orb, e, time);
            if (elementCount(e) == 1) return;
        }
        if (hasElement(e, Element.WIND) && !orb) {
            renderWindProjectileWake(vc, cam, p, dir, size, lance, time);
            if (elementCount(e) == 1) return;
        }
        if (orb) {
            renderOrb(vc,cam,p,size*1.35f,e,time);
            renderTapered(vc,cam,p.subtract(dir.scale(size*2.6)),p.subtract(dir.scale(size*.25)),size*.32f,.01f,e,90,185);
        } else if (lance) {
            double len = Math.max(2.8, 4.0 + e.secondary*1.8);
            Vec3 tail=p.subtract(dir.scale(len*.72)),tip=p.add(dir.scale(len*.28));
            renderTapered(vc,cam,tail,tip,size*.30f,.012f,e,95,225);
            Vec3 side=orthogonal(dir), up=dir.cross(side).normalize();
            addColoredQuad(vc,cam,p.subtract(dir.scale(len*.28)).add(side.scale(size*.9)),p.add(dir.scale(len*.10)),p.subtract(dir.scale(len*.28)).subtract(side.scale(size*.9)),p.subtract(dir.scale(len*.45)),e,150);
            addColoredQuad(vc,cam,p.subtract(dir.scale(len*.28)).add(up.scale(size*.75)),p.add(dir.scale(len*.08)),p.subtract(dir.scale(len*.28)).subtract(up.scale(size*.75)),p.subtract(dir.scale(len*.45)),e,125);
        } else {
            renderTapered(vc,cam,p.subtract(dir.scale(1.7*size)),p.add(dir.scale(.35*size)),size*.34f,size*.07f,e,100,215);
            renderTapered(vc,cam,p.subtract(dir.scale(2.8*size)),p.subtract(dir.scale(.2*size)),size*.18f,.01f,e,55,145);
        }
    }

    /** Crystal-prism body adapted from Dragon Flux's Ice VFX: hard faces, bright ridge, real point. */
    private static void renderIceProjectile(VertexConsumer vc, Vec3 cam, Vec3 p, Vec3 dir, float size,
                                            boolean lance, boolean orb, Effect e, double time) {
        if (orb) {
            double r=Math.max(.16,size*.9);
            Vec3 up=orthogonal(dir), right=dir.cross(up).normalize();
            renderCrystalSegment(vc,cam,p.subtract(up.scale(r*.72)),p.add(up.scale(r*.78)),r*.30,170);
            renderCrystalSegment(vc,cam,p.subtract(right.scale(r*.68)),p.add(right.scale(r*.72)),r*.27,150);
            renderCrystalSegment(vc,cam,p.subtract(dir.scale(r*.62)),p.add(dir.scale(r*.72)),r*.25,205);
            return;
        }
        double len=lance?Math.max(2.8,4.0+e.secondary*1.8):Math.max(.85,size*5.3);
        double w=(lance?.22:.30)*Math.max(.55,size*2.8);
        Vec3 tail=p.subtract(dir.scale(len*(lance?.62:.58))), tip=p.add(dir.scale(len*(lance?.38:.42)));
        renderCrystalSegment(vc,cam,tail,tip,w,lance?225:185);
        if(lance){
            Vec3 back=p.subtract(dir.scale(len*.82));
            renderCrystalSegment(vc,cam,back,p.subtract(dir.scale(len*.28)),w*.42,125);
        }
    }

    /** Four hard crystal faces taper to a small tip, so ice reads as material rather than glow. */
    private static void renderCrystalSegment(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,double w,int alpha){
        Vec3 d=b.subtract(a);if(d.lengthSqr()<1e-8)return;Vec3 dir=d.normalize();Vec3 u=orthogonal(dir).scale(w),v=dir.cross(u).normalize().scale(w);
        Vec3 tip=b;
        addQuad(vc,cam,a.add(u),a.add(v),tip.add(v.scale(.14)),tip.add(u.scale(.14)),0x55b4f5,alpha);
        addQuad(vc,cam,a.add(v),a.subtract(u),tip.subtract(u.scale(.14)),tip.add(v.scale(.14)),0xafe6ff,Math.min(245,alpha+30));
        addQuad(vc,cam,a.subtract(u),a.subtract(v),tip.subtract(v.scale(.14)),tip.subtract(u.scale(.14)),0x468fe6,alpha);
        addQuad(vc,cam,a.subtract(v),a.add(u),tip.add(u.scale(.14)),tip.subtract(v.scale(.14)),0xe1faff,Math.min(250,alpha+45));
        addRibbon(vc,cam,a,tip,u.normalize(),Math.max(.009,w*.08),0xf6ffff,Math.min(255,alpha+55));
    }

    /** Angular stone mass; deliberately asymmetrical so Earth does not read as a glowing orb. */
    private static void renderEarthProjectile(VertexConsumer vc,Vec3 cam,Vec3 p,Vec3 dir,float size,
                                              boolean lance,boolean orb,Effect e,double time){
        double len=lance?Math.max(2.5,3.5+e.secondary*1.4):Math.max(.45,size*(orb?2.7:3.5));
        double r=Math.max(.13,size*(orb?.95:.72));
        Vec3 f=dir.normalize(),u=orthogonal(f),v=f.cross(u).normalize();
        if(lance){
            Vec3 base=p.subtract(f.scale(len*.62)),shoulder=p.add(f.scale(len*.05)),tip=p.add(f.scale(len*.38));
            renderRockPrism(vc,cam,base,shoulder,u,v,r,e.seed);
            renderRockPoint(vc,cam,shoulder,tip,u,v,r*.88,e.seed+17);
        }else{
            Vec3 a=p.subtract(f.scale(len*.45)),b=p.add(f.scale(len*.45));
            renderRockPrism(vc,cam,a,b,u,v,r,e.seed+(long)e.age/3);
        }
    }

    private static void renderRockPrism(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,Vec3 u,Vec3 v,double r,long seed){
        Random rnd=new Random(seed);double r1=r*(.82+rnd.nextDouble()*.30),r2=r*(.72+rnd.nextDouble()*.34);
        Vec3 aU=u.scale(r1),aV=v.scale(r1*.82),bU=u.scale(r2),bV=v.scale(r2*.88);
        addQuad(vc,cam,a.add(aU),a.add(aV),b.add(bV),b.add(bU),0x8a6a3b,225);
        addQuad(vc,cam,a.add(aV),a.subtract(aU),b.subtract(bU),b.add(bV),0xb4935a,235);
        addQuad(vc,cam,a.subtract(aU),a.subtract(aV),b.subtract(bV),b.subtract(bU),0x634a2d,230);
        addQuad(vc,cam,a.subtract(aV),a.add(aU),b.add(bU),b.subtract(bV),0x9d7b49,220);
        addQuad(vc,cam,a.add(aU),a.add(aV),a.subtract(aU),a.subtract(aV),0x725334,220);
        addQuad(vc,cam,b.add(bU),b.subtract(bV),b.subtract(bU),b.add(bV),0xc0a06a,238);
    }

    private static void renderRockPoint(VertexConsumer vc,Vec3 cam,Vec3 base,Vec3 tip,Vec3 u,Vec3 v,double r,long seed){
        Vec3 U=u.scale(r),V=v.scale(r*.80),tinyU=u.scale(r*.07),tinyV=v.scale(r*.06);
        addQuad(vc,cam,base.add(U),base.add(V),tip.add(tinyV),tip.add(tinyU),0x9b7746,230);
        addQuad(vc,cam,base.add(V),base.subtract(U),tip.subtract(tinyU),tip.add(tinyV),0xc0a06a,240);
        addQuad(vc,cam,base.subtract(U),base.subtract(V),tip.subtract(tinyV),tip.subtract(tinyU),0x60462a,230);
        addQuad(vc,cam,base.subtract(V),base.add(U),tip.add(tinyU),tip.subtract(tinyV),0x86643a,225);
    }

    /** Void is absence rather than dark fire: a near-black missing core with a thin violet rim and inward-curling strands. */
    private static void renderVoidProjectile(VertexConsumer vc, Vec3 cam, Vec3 p, Vec3 dir, float size,
                                             boolean lance, boolean orb, Effect e, double time) {
        Vec3 u=orthogonal(dir), v=dir.cross(u).normalize();
        double r=Math.max(.10,size*(orb?1.15:.82));
        double pulse=.90+.10*Math.sin(time*.31);
        Vec3 U=u.scale(r*pulse), V=v.scale(r*pulse);
        // opaque-ish absence core; this intentionally has less glow than every ordinary element
        addQuad(vc,cam,p.add(U),p.add(V),p.subtract(U),p.subtract(V),0x050208,225);
        addQuad(vc,cam,p.add(V),p.add(dir.scale(r)),p.subtract(V),p.subtract(dir.scale(r)),0x0a0310,215);
        // thin rim planes orbit the missing center instead of filling it with particles
        for(int i=0;i<3;i++){
            double a=time*.09+i*Math.PI*2/3.0;
            Vec3 side=u.scale(Math.cos(a)).add(v.scale(Math.sin(a))).normalize();
            Vec3 cross=dir.cross(side).normalize();
            double rr=r*(1.25+.10*Math.sin(time*.17+i));
            addRibbon(vc,cam,p.subtract(cross.scale(rr)),p.add(cross.scale(rr)),side,.018,0x8f4fc7,155);
            addRibbon(vc,cam,p.subtract(cross.scale(rr*.72)),p.add(cross.scale(rr*.72)),side,.007,0xd9a8ff,205);
        }
        double tailLen=lance?Math.max(2.5,3.7+e.secondary*1.6):Math.max(.55,size*3.5);
        Vec3 tail=p.subtract(dir.scale(tailLen));
        for(int i=0;i<3;i++){
            double phase=time*.13+i*Math.PI*2/3.0;
            Vec3 offset=u.scale(Math.cos(phase)*r*.55).add(v.scale(Math.sin(phase)*r*.55));
            addRibbon(vc,cam,tail.add(offset.scale(.25)),p.add(offset),sideForSegment(tail,p,cam),.018,0x6d2fa2,90);
        }
    }

    private static void renderVoidBeam(VertexConsumer vc, Vec3 cam, Vec3 start, Vec3 end, float radius, double time) {
        Vec3 delta=end.subtract(start); if(delta.lengthSqr()<1e-8)return; Vec3 dir=delta.normalize();
        Vec3 u=orthogonal(dir),v=dir.cross(u).normalize();
        double core=Math.max(.025,radius*.34);
        addRibbon(vc,cam,start,end,u,core*2.2,0x050208,220);
        addRibbon(vc,cam,start,end,v,core*1.8,0x0a0310,220);
        int segs=Math.min(70,Math.max(14,(int)(delta.length()*3.0)));
        for(int lane=0;lane<3;lane++){
            Vec3 prev=start; double off=lane*Math.PI*2/3.0;
            for(int i=1;i<=segs;i++){
                double f=i/(double)segs,a=time*.10+off+f*Math.PI*5.0;
                double rr=Math.max(.06,radius*(.85+.12*Math.sin(f*Math.PI)));
                Vec3 q=start.lerp(end,f).add(u.scale(Math.cos(a)*rr)).add(v.scale(Math.sin(a)*rr));
                addRibbon(vc,cam,prev,q,sideForSegment(prev,q,cam),lane==0?.020:.014,0x8f4fc7,105);
                prev=q;
            }
        }
        // narrow pale edge tells the eye where the void boundary is without turning it into a normal glowing laser
        addRibbon(vc,cam,start,end,u,.009,0xc990ff,185);
    }

    private static void renderBeam(VertexConsumer vc, Vec3 cam, Vec3 start, Vec3 end, float radius, Effect e, double time) {
        Vec3 delta=end.subtract(start); double len=delta.length(); if(len<.04)return; Vec3 dir=delta.scale(1/len);
        if (hasElement(e, Element.LIGHTNING)) {
            renderLightningBeam(vc, cam, start, end, e, time);
            if (elementCount(e) == 1) return;
        }
        if (hasElement(e, Element.WIND)) {
            renderWindBeam(vc, cam, start, end, radius, e, time);
            if (elementCount(e) == 1) return;
        }
        if (hasElement(e, Element.VOID)) {
            renderVoidBeam(vc, cam, start, end, radius, time);
            if (elementCount(e) == 1) return;
        }
        Vec3 u=orthogonal(dir), v=dir.cross(u).normalize();
        int segs=Math.min(64,Math.max(7,(int)(len/1.15)));
        for(int i=0;i<segs;i++){
            double t0=i/(double)segs,t1=(i+1)/(double)segs;
            Vec3 p0=start.lerp(end,t0),p1=start.lerp(end,t1);
            double wobble=Math.sin(time*.42+i*.78)*radius*.12;
            p0=p0.add(u.scale(wobble)); p1=p1.add(u.scale(Math.sin(time*.42+(i+1)*.78)*radius*.12));
            float taper=(float)Math.max(.12,Math.min(1.0,(t0*len)/1.1));
            addPaletteRibbon(vc,cam,p0,p1,u,radius*1.15f*taper,e,65,0);
            addPaletteRibbon(vc,cam,p0,p1,v,radius*.62f*taper,e,150,1);
            addPaletteRibbon(vc,cam,p0,p1,u,radius*.24f*taper,e,225,2);
        }
        renderOrb(vc,cam,end,Math.max(.11f,radius*.7f),e,time);
    }

    /** Layered translucent current lines adapted from Dragon Flux's Storm wind visuals. */
    private static void renderWindBeam(VertexConsumer vc,Vec3 cam,Vec3 start,Vec3 end,float radius,Effect e,double time){
        Vec3 delta=end.subtract(start);if(delta.lengthSqr()<1e-8)return;Vec3 dir=delta.normalize();Vec3 right=orthogonal(dir),up=dir.cross(right).normalize();
        int lanes=7;double width=Math.max(.08,radius*1.8);
        for(int i=0;i<lanes;i++){
            double f=(i-(lanes-1)/2.0)/((lanes-1)/2.0);double phase=time*.075+i*.9;
            Vec3 a=start.add(right.scale(f*.05));
            Vec3 b=end.add(right.scale(f*width)).add(up.scale(Math.sin(phase)*width*.26));
            addRibbon(vc,cam,a,b,sideForSegment(a,b,cam),.028+Math.abs(f)*.009,0xbcecff,105);
            addRibbon(vc,cam,a,b,sideForSegment(a,b,cam),.009,0xf7ffff,185);
        }
    }

    /** Wind carried by a bolt/lance reads as flowing air around the body, not merely pale-blue recoloring. */
    private static void renderWindProjectileWake(VertexConsumer vc, Vec3 cam, Vec3 p, Vec3 dir, float size, boolean lance, double time) {
        Vec3 u=orthogonal(dir),v=dir.cross(u).normalize();
        double len=lance?Math.max(2.4,size*9.0):Math.max(.8,size*4.5);
        double r=Math.max(.10,size*(lance?.75:1.05));
        for(int strand=0;strand<5;strand++){
            double phase=time*.30+strand*Math.PI*2/5.0;
            Vec3 prev=null;
            for(int i=0;i<=8;i++){
                double f=i/8.0;
                double back=(1.0-f)*len;
                double rr=r*(.25+.75*(1.0-f));
                double a=phase+f*Math.PI*2.2;
                Vec3 q=p.subtract(dir.scale(back)).add(u.scale(Math.cos(a)*rr)).add(v.scale(Math.sin(a)*rr));
                if(prev!=null){
                    Vec3 face=sideForSegment(prev,q,cam);
                    addRibbon(vc,cam,prev,q,face,.025,0xbcecff,90);
                    addRibbon(vc,cam,prev,q,face,.009,0xf7ffff,185);
                }
                prev=q;
            }
        }
    }

    /**
     * A rain strike is a moving falling object, not a beam drawn from cloud to ground.
     * Ice becomes hail/crystal shards, fire becomes a falling ember/comet, earth a rock,
     * lightning remains a brief forked strike, and other elements use a compact streak.
     * secondary > .5 means sveira was spoken and the drop corkscrews while falling.
     */
    private static void renderRainStrike(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        double progress=Mth.clamp((e.age+(time-e.age))/(double)Math.max(1,e.total),0,1);
        Vec3 axis=e.b.subtract(e.a); if(axis.lengthSqr()<1e-8)return;
        Vec3 dir=axis.normalize();
        Vec3 p=e.a.add(axis.scale(progress));
        if(e.secondary>.5f){
            Vec3 u=orthogonal(dir),v=dir.cross(u).normalize();
            double phase=(e.seed&0xffff)*.001+progress*Math.PI*4.0+time*.12;
            double rr=.30*Math.sin(Math.PI*progress);
            p=p.add(u.scale(Math.cos(phase)*rr)).add(v.scale(Math.sin(phase)*rr));
        }
        float size=Math.max(.09f,e.primary);

        if(hasElement(e,Element.LIGHTNING)){
            // Lightning rain is naturally a full atmospheric strike; use the same chain-lightning language.
            renderLightningBolt(vc,cam,e.a,e.b,.035+size*.12,1,e.seed);
            if(elementCount(e)==1)return;
        }
        if(hasElement(e,Element.ICE)){
            Vec3 tail=p.subtract(dir.scale(.48+size*1.6)),tip=p.add(dir.scale(.12));
            renderCrystalSegment(vc,cam,tail,tip,.07+size*.24,215);
            if(elementCount(e)==1)return;
        }
        if(hasElement(e,Element.EARTH)){
            Vec3 u=orthogonal(dir),v=dir.cross(u).normalize();
            renderRockPrism(vc,cam,p.subtract(dir.scale(.20)),p.add(dir.scale(.20)),u,v,.10+size*.30,e.seed);
            if(elementCount(e)==1)return;
        }
        if(hasElement(e,Element.WIND)){
            renderWindProjectileWake(vc,cam,p,dir,size,false,time);
            if(elementCount(e)==1)return;
        }
        if(hasElement(e,Element.VOID)){
            renderVoidProjectile(vc,cam,p,dir,size,false,false,e,time);
            if(elementCount(e)==1)return;
        }

        // Fire/water/force/etc.: a compact falling body with a short tail, never a sky-to-ground line.
        renderTapered(vc,cam,p.subtract(dir.scale(.55+size*1.8)),p.add(dir.scale(.10)),size*.42f,.01f,e,95,220);
        renderOrb(vc,cam,p,Math.max(.07f,size*.48f),e,time);
    }

    private static void renderTether(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        Vec3 start=visualHand(e),end=e.b; int segs=20; Vec3 prev=start;
        Vec3 straight=end.subtract(start); Vec3 side=straight.lengthSqr()<1e-7?new Vec3(1,0,0):orthogonal(straight.normalize());
        for(int i=1;i<=segs;i++){
            double t=i/(double)segs;
            double sag=Math.sin(Math.PI*t)*(.16+.07*Math.min(5,e.primary));
            double wave=Math.sin(time*.35+t*10+e.seed*.0001)*(.04+.02*e.primary);
            Vec3 p=start.lerp(end,t).add(0,-sag,0).add(side.scale(wave));
            Vec3 face=sideForSegment(prev,p,cam); float width=(float)(Math.max(.035,e.primary*.055)*(1-t*.45));
            addPaletteRibbon(vc,cam,prev,p,face,width*1.9f,e,70,0);
            addPaletteRibbon(vc,cam,prev,p,face,width*.75f,e,205,1);
            prev=p;
        }
    }

    private static void renderClaws(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        Minecraft mc=Minecraft.getInstance(); Entity owner=mc.level==null?null:mc.level.getEntity(e.ownerId); if(owner==null)return;
        Vec3 look=SpellRenderAnchors.forward(e.ownerId,owner.getLookAngle()); Vec3 side=SpellRenderAnchors.right(e.ownerId),up=SpellRenderAnchors.up(e.ownerId);
        float size=Math.max(.55f,e.primary); boolean first=SpellRenderAnchors.localFirstPerson(e.ownerId);
        boolean[] hands=first?new boolean[]{false}:new boolean[]{true,false};
        for(boolean left:hands){
            Vec3 h=SpellRenderAnchors.hand(e.ownerId,owner.position().add(0,owner.getBbHeight()*.55,0),left); double sign=left?-1:1;
            for(int claw=0;claw<3;claw++){
                double off=(claw-1)*.075*size; Vec3 a=h.add(side.scale(off));
                Vec3 b=a.add(look.scale(.42*size)).add(up.scale(.015*size));
                Vec3 c=b.add(side.scale(sign*(.07+.025*claw)*size)).add(look.scale(.26*size)).add(up.scale(.06*size));
                renderCurve(vc,cam,a,b,c,.045f*size,e,time+claw*.5);
            }
        }
    }

    private static void renderSpiral(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        // Spiral is a motion modifier, so its axis can be any existing form's path: a horizontal
        // ray, a diagonal lance/tether, or a vertical rain column. e.primary is radius and
        // e.secondary is the number of turns rather than a hard-coded vertical height.
        Vec3 axis=e.b.subtract(e.a); double length=axis.length();
        if(length<.04){axis=new Vec3(0,1,0);length=1.5;}
        Vec3 dir=axis.normalize(),u=orthogonal(dir),v=dir.cross(u).normalize();
        double radius=Math.max(.14,e.primary),turns=Math.max(1.0,e.secondary);
        int bands=3,segs=Math.min(120,Math.max(28,(int)(length*5+turns*10)));
        for(int band=0;band<bands;band++){
            Vec3 prev=null; double off=band*Math.PI*2/bands;
            for(int i=0;i<=segs;i++){
                double f=i/(double)segs;
                double angle=time*.16+off+f*Math.PI*2*turns;
                double breathe=.88+.12*Math.sin(time*.11+f*Math.PI*2+band);
                double rr=radius*breathe;
                Vec3 p=e.a.add(axis.scale(f)).add(u.scale(Math.cos(angle)*rr)).add(v.scale(Math.sin(angle)*rr));
                if(prev!=null){
                    Vec3 side=sideForSegment(prev,p,cam);
                    float width=(float)(.028+Math.min(.055,radius*.06));
                    addPaletteRibbon(vc,cam,prev,p,side,width*1.9f,e,70,band);
                    addPaletteRibbon(vc,cam,prev,p,side,width*.68f,e,210,band+1);
                }
                prev=p;
            }
        }
    }


    /** kringferd: several coherent strands orbit the chosen form's existing path. */
    private static void renderOrbit(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        Vec3 axis=e.b.subtract(e.a); double len=axis.length(); if(len<.04)return;
        Vec3 dir=axis.normalize(),u=orthogonal(dir),v=dir.cross(u).normalize();
        double radius=Math.max(.18,e.primary),turns=Math.max(1.0,e.secondary); int strands=3;
        int segs=Math.min(96,Math.max(24,(int)(len*4+turns*10)));
        for(int strand=0;strand<strands;strand++){
            Vec3 prev=null; double phase=strand*Math.PI*2/strands+time*.22;
            for(int i=0;i<=segs;i++){
                double f=i/(double)segs,angle=phase+f*Math.PI*2*turns;
                double rr=radius*(.82+.18*Math.sin(time*.13+i*.31+strand));
                Vec3 p=e.a.add(axis.scale(f)).add(u.scale(Math.cos(angle)*rr)).add(v.scale(Math.sin(angle)*rr));
                if(prev!=null){Vec3 side=sideForSegment(prev,p,cam);addPaletteRibbon(vc,cam,prev,p,side,.032,e,115,strand);addPaletteRibbon(vc,cam,prev,p,side,.012,e,225,strand+1);}
                prev=p;
            }
        }
    }

    /** sveigja: draw a visibly bent path while preserving the form that owns the path. */
    private static void renderRedirect(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        Vec3 d=e.b.subtract(e.a); if(d.lengthSqr()<1e-8)return; Vec3 dir=d.normalize();
        Vec3 side=orthogonal(dir); double sign=(e.seed&1L)==0?1:-1; double bend=Math.max(.35,e.primary);
        Vec3 control=e.a.lerp(e.b,.5).add(side.scale(bend*sign));
        renderCurve(vc,cam,e.a,control,e.b,Math.max(.025f,e.secondary),e,time);
    }

    /** Physical construction cue: the wall grows upward from its base instead of popping into view. */
    private static void renderWallRise(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        double progress=Mth.clamp((e.age+Math.min(1.0,time-e.age))/(double)Math.max(1,e.total),0,1);
        Vec3 edge=e.b.subtract(e.a); if(edge.lengthSqr()<1e-8)return; Vec3 side=edge.normalize();
        double height=Math.max(.5,e.secondary)*Math.min(1.0,progress*1.55); int columns=Math.max(3,(int)Math.ceil(edge.length()*1.7));
        for(int i=0;i<=columns;i++){double f=i/(double)columns;Vec3 base=e.a.lerp(e.b,f),top=base.add(0,height,0);
            if(hasElement(e,Element.EARTH)){Vec3 u=orthogonal(new Vec3(0,1,0)),v=new Vec3(0,1,0).cross(u).normalize();renderRockPrism(vc,cam,base,top,u,v,Math.max(.10,e.primary*.42),e.seed+i*19L);}
            else {addPaletteRibbon(vc,cam,base,top,side,.06+e.primary*.10,e,125,i);}
        }
        Vec3 topA=e.a.add(0,height,0),topB=e.b.add(0,height,0);addPaletteRibbon(vc,cam,topA,topB,new Vec3(0,1,0),.035+e.primary*.08,e,175,1);
    }

    /** Pillar construction cue; earth reads as stacked angular matter rather than a particle column. */
    private static void renderPillarRise(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        double progress=Mth.clamp((e.age+Math.min(1.0,time-e.age))/(double)Math.max(1,e.total),0,1);
        Vec3 full=e.b.subtract(e.a); if(full.lengthSqr()<1e-8)return; Vec3 top=e.a.add(full.scale(Math.min(1.0,progress*1.55)));
        Vec3 dir=full.normalize(),u=orthogonal(dir),v=dir.cross(u).normalize();double r=Math.max(.18,e.primary);
        if(hasElement(e,Element.EARTH)) renderRockPrism(vc,cam,e.a,top,u,v,r,e.seed+(long)e.age/2);
        else if(hasElement(e,Element.ICE)) renderCrystalSegment(vc,cam,e.a,top,r,205);
        else {for(int i=0;i<4;i++){double a=i*Math.PI*.5+time*.03;Vec3 off=u.scale(Math.cos(a)*r).add(v.scale(Math.sin(a)*r));addPaletteRibbon(vc,cam,e.a.add(off),top.add(off),sideForSegment(e.a.add(off),top.add(off),cam),.035,e,155,i);}}
    }

    /** Wind push: broad current sheets diverge toward the far end instead of looking like dots. */
    private static void renderGale(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        Vec3 d=e.b.subtract(e.a);if(d.lengthSqr()<1e-8)return;Vec3 dir=d.normalize(),right=orthogonal(dir),up=dir.cross(right).normalize();
        double spread=Math.max(.5,e.primary);int lanes=9;
        for(int i=0;i<lanes;i++){double f=(i-(lanes-1)/2.0)/((lanes-1)/2.0),phase=time*.16+i*.7;Vec3 a=e.a.add(right.scale(f*.10));Vec3 b=e.b.add(right.scale(f*spread)).add(up.scale(Math.sin(phase)*spread*.14));Vec3 side=sideForSegment(a,b,cam);addPaletteRibbon(vc,cam,a,b,side,.045,e,82,i);addPaletteRibbon(vc,cam,a,b,side,.014,e,185,i+1);}
    }

    /** Rising air column: layered corkscrewing currents, suitable for lyfta+vindr and similar wording. */
    private static void renderUpdraft(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        Vec3 axis=e.b.subtract(e.a);double h=axis.length();if(h<.1)return;Vec3 dir=axis.normalize(),u=orthogonal(dir),v=dir.cross(u).normalize();double radius=Math.max(.55,e.primary);
        for(int strand=0;strand<6;strand++){Vec3 prev=null;for(int i=0;i<=28;i++){double f=i/28.0,a=time*.18+strand*Math.PI/3+f*Math.PI*4.5;double rr=radius*(.45+.45*f);Vec3 p=e.a.add(axis.scale(f)).add(u.scale(Math.cos(a)*rr)).add(v.scale(Math.sin(a)*rr));if(prev!=null){Vec3 side=sideForSegment(prev,p,cam);addPaletteRibbon(vc,cam,prev,p,side,.035,e,95,strand);addPaletteRibbon(vc,cam,prev,p,side,.011,e,190,strand+1);}prev=p;}}
    }

    private static void renderShell(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        Vec3 c=e.a; Minecraft mc=Minecraft.getInstance(); Entity owner=mc.level==null?null:mc.level.getEntity(e.ownerId);
        if(owner!=null && e.a.distanceToSqr(owner.position())<9) c=owner.position().add(0,owner.getBbHeight()*.5,0);
        double r=Math.max(.65,e.primary),half=Math.max(.65,e.secondary);
        int segs=10;
        Vec3 top=c.add(0,half,0),bottom=c.add(0,-half,0);
        Vec3[] upper=new Vec3[segs],mid=new Vec3[segs],lower=new Vec3[segs];
        double spin=time*.015;
        for(int i=0;i<segs;i++){
            double a=Math.PI*2*i/segs+spin;
            upper[i]=c.add(Math.cos(a)*r*.72,half*.48,Math.sin(a)*r*.72);
            mid[i]=c.add(Math.cos(a+Math.PI/segs)*r,0,Math.sin(a+Math.PI/segs)*r);
            lower[i]=c.add(Math.cos(a)*r*.72,-half*.48,Math.sin(a)*r*.72);
        }
        for(int i=0;i<segs;i++){
            int n=(i+1)%segs;
            // translucent facets make a real shell/surface instead of seven floating rings
            addQuad(vc,cam,top,upper[i],upper[n],top,color(e,i),36);
            addQuad(vc,cam,upper[i],mid[i],mid[n],upper[n],color(e,i),42);
            addQuad(vc,cam,mid[i],lower[i],lower[n],mid[n],color(e,i+1),42);
            addQuad(vc,cam,lower[i],bottom,bottom,lower[n],color(e,i+1),36);
            // brighter structural ribs around the facets
            addPaletteRibbon(vc,cam,upper[i],mid[i],sideForSegment(upper[i],mid[i],cam),.020,e,135,i);
            addPaletteRibbon(vc,cam,mid[i],lower[i],sideForSegment(mid[i],lower[i],cam),.020,e,135,i+1);
            addPaletteRibbon(vc,cam,top,upper[i],sideForSegment(top,upper[i],cam),.014,e,110,i+2);
            addPaletteRibbon(vc,cam,lower[i],bottom,sideForSegment(lower[i],bottom,cam),.014,e,110,i+3);
        }
    }

    private static void renderBurst(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        double progress=Mth.clamp(e.age/(double)Math.max(1,e.total),0,1); double maxR=Math.max(.5,e.primary); double travel=maxR*(.12+.95*(1-Math.pow(1-progress,3)));
        Random r=new Random(e.seed); int rays=Math.min(26,Math.max(10,(int)(maxR*2.0)));
        for(int i=0;i<rays;i++){
            double yaw=r.nextDouble()*Math.PI*2,y=(r.nextDouble()-.45)*1.15; Vec3 d=new Vec3(Math.cos(yaw),y,Math.sin(yaw)).normalize();
            Vec3 a=e.a.add(d.scale(travel*.10)),b=e.a.add(d.scale(travel*(.65+r.nextDouble()*.4))); Vec3 s=sideForSegment(a,b,cam);
            addPaletteRibbon(vc,cam,a,b,s,.035+.025*(1-progress),e,155,i);
        }
        renderOrb(vc,cam,e.a,(float)Math.max(.08,maxR*.10*(1-progress*.6)),e,time);
    }

    /** Moving ring/disc whose edge visibly rotates and carries small teeth. */
    private static void renderCuttingRing(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        double progress=Mth.clamp((e.age+(time-e.age))/(double)Math.max(1,e.total),0,1);
        Vec3 path=e.b.subtract(e.a);
        Vec3 dir=path.lengthSqr()<1e-8?new Vec3(0,1,0):path.normalize();
        Vec3 center=e.a.lerp(e.b,progress);
        Vec3 u=orthogonal(dir),v=dir.cross(u).normalize();
        double radius=Math.max(.35,e.primary);
        float width=Math.max(.025f,e.secondary);
        int segs=28;
        double phase=time*.46 + (e.seed&255)*.002;
        Vec3 prev=null;
        for(int i=0;i<=segs;i++){
            double a=phase+Math.PI*2*i/segs;
            Vec3 radial=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 p=center.add(radial.scale(radius));
            if(prev!=null){
                Vec3 face=sideForSegment(prev,p,cam);
                addPaletteRibbon(vc,cam,prev,p,face,width*2.15,e,82,i);
                addPaletteRibbon(vc,cam,prev,p,face,width*.72,e,230,i+1);
            }
            // Every other segment grows a short bright tooth outside the rim; rotating phase makes
            // the cutting motion obvious without using a separate particle ring.
            if(i<segs && (i&1)==0){
                Vec3 inner=center.add(radial.scale(radius*.96));
                Vec3 tip=center.add(radial.scale(radius*1.18));
                Vec3 face=sideForSegment(inner,tip,cam);
                addPaletteRibbon(vc,cam,inner,tip,face,width*.82,e,235,i+2);
            }
            prev=p;
        }
        // A faint axial hub keeps the disc readable edge-on.
        Vec3 hubA=center.subtract(dir.scale(width*1.8)),hubB=center.add(dir.scale(width*1.8));
        renderTapered(vc,cam,hubA,hubB,width*1.45f,width*1.45f,e,65,190);
    }

    private static void renderRing(VertexConsumer vc, Vec3 cam, Vec3 center, double radius, float width, Effect e, double time) {
        int segs=Math.min(80,Math.max(20,(int)(radius*14))); Vec3 prev=null;
        for(int i=0;i<=segs;i++){
            double a=Math.PI*2*i/segs; Vec3 p=center.add(Math.cos(a)*radius,.035+Math.sin(a*3+time*.12)*.025,Math.sin(a)*radius);
            if(prev!=null){Vec3 radial=new Vec3(Math.cos(a),0,Math.sin(a));addPaletteRibbon(vc,cam,prev,p,radial,width*1.55f,e,65,i);addPaletteRibbon(vc,cam,prev,p,radial,width*.62f,e,190,i+1);} prev=p;
        }
    }

    private static void renderCloud(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        double r=Math.max(1,e.primary); Random random=new Random(e.seed);
        for(int i=0;i<9;i++){
            double a=random.nextDouble()*Math.PI*2,rr=r*(.2+random.nextDouble()*.75); Vec3 base=e.a.add(Math.cos(a)*rr,(random.nextDouble()-.5)*.7,Math.sin(a)*rr);
            Vec3 top=base.add(Math.sin(time*.08+i)*.18,.7+random.nextDouble()*.8,Math.cos(time*.07+i)*.18); addPaletteRibbon(vc,cam,base,top,sideForSegment(base,top,cam),.11,e,70,i);
        }
        renderRing(vc,cam,e.a,r,.025f,e,time);
    }

    private static void renderAura(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        Minecraft mc=Minecraft.getInstance(); Entity owner=mc.level==null?null:mc.level.getEntity(e.ownerId); Vec3 c=owner==null?e.a:owner.position().add(0,.08,0); double r=Math.max(.8,e.primary);
        renderRing(vc,cam,c,r,.035f,e,time);
        renderRing(vc,cam,c.add(0,.22,0),r*.72,.025f,e,-time*.8);
        for(int i=0;i<4;i++){
            double a=time*.07+i*Math.PI*.5; Vec3 b=c.add(Math.cos(a)*r*.65,0,Math.sin(a)*r*.65),t=b.add(Math.sin(time*.19+i)*.12,.75+r*.12,Math.cos(time*.17+i)*.12);
            addPaletteRibbon(vc,cam,b,t,sideForSegment(b,t,cam),.045,e,120,i);
        }
    }

    private static void renderSigil(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        double r=Math.max(.55,e.primary); Vec3 c=e.a.add(0,.035,0); int points=6; Vec3[] p=new Vec3[points];
        for(int i=0;i<points;i++){double a=time*.01+Math.PI*2*i/points;p[i]=c.add(Math.cos(a)*r,0,Math.sin(a)*r);}
        for(int i=0;i<points;i++){Vec3 a=p[i],b=p[(i+1)%points];addPaletteRibbon(vc,cam,a,b,new Vec3((a.x-c.x),0,(a.z-c.z)).normalize(),.025,e,195,i);}
        for(int i=0;i<3;i++){Vec3 a=p[i],b=p[i+3];addPaletteRibbon(vc,cam,a,b,new Vec3(0,1,0),.014,e,125,i);}
        renderOrb(vc,cam,c.add(0,.08,0),.09f,e,time);
    }

    private static void renderConvergence(VertexConsumer vc, Vec3 cam, Effect e, double time) {
        double r=Math.max(1,e.primary); Random random=new Random(e.seed); int streams=14;
        for(int i=0;i<streams;i++){
            double yaw=random.nextDouble()*Math.PI*2,pitch=(random.nextDouble()-.5)*.8; Vec3 d=new Vec3(Math.cos(yaw),pitch,Math.sin(yaw)).normalize();
            double pulse=.65+.35*Math.sin(time*.12+i); Vec3 start=e.a.add(d.scale(r*pulse)),end=e.a.add(d.scale(.12));
            addPaletteRibbon(vc,cam,start,end,sideForSegment(start,end,cam),.025,e,145,i);
        }
        renderOrb(vc,cam,e.a,.12f+(float)(.03*Math.sin(time*.3)),e,time);
    }

    private static Vec3 visualHand(Effect e) { return SpellRenderAnchors.rightHand(e.ownerId,e.a); }

    private static void renderOrb(VertexConsumer vc, Vec3 cam, Vec3 p, float radius, Effect e, double time) {
        float pulse=(float)(1+.07*Math.sin(time*.5)),r=radius*pulse; Vec3[] axes={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
        for(int i=0;i<3;i++){Vec3 u=axes[i],v=axes[(i+1)%3];addPaletteDiamond(vc,cam,p,u.scale(r),v.scale(r),e,75,i);addPaletteDiamond(vc,cam,p,u.scale(r*.55),v.scale(r*.55),e,210,i+1);}
    }

    private static void renderTapered(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,float wa,float wb,Effect e,int outerA,int innerA){
        Vec3 d=b.subtract(a);if(d.lengthSqr()<1e-7)return;d=d.normalize();Vec3 u=orthogonal(d),v=d.cross(u).normalize();
        addPaletteTaper(vc,cam,a,b,u.scale(wa*1.35),u.scale(wb*1.35),e,outerA,0);addPaletteTaper(vc,cam,a,b,v.scale(wa*.82),v.scale(wb*.82),e,innerA,1);addPaletteTaper(vc,cam,a,b,u.scale(wa*.32),u.scale(wb*.32),e,235,2);
    }

    private static void renderCurve(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,Vec3 c,float width,Effect e,double time){
        Vec3 prev=a;for(int i=1;i<=9;i++){double t=i/9.0,u=1-t;Vec3 p=a.scale(u*u).add(b.scale(2*u*t)).add(c.scale(t*t));Vec3 s=sideForSegment(prev,p,cam);addPaletteRibbon(vc,cam,prev,p,s,width*1.8,e,72,i);addPaletteRibbon(vc,cam,prev,p,s,width*.62,e,220,i+1);prev=p;}
    }

    /** Dragon Flux-style coherent lightning: one jagged trunk with restrained side forks. */
    private static void renderLightningBolt(VertexConsumer vc,Vec3 cam,Vec3 from,Vec3 to,double width,int branches,long seed){
        Vec3 delta=to.subtract(from);double len=delta.length();if(len<.03)return;Vec3 dir=delta.scale(1/len),u=orthogonal(dir),v=dir.cross(u).normalize();
        int segments=Math.min(48,Math.max(5,(int)Math.ceil(len*1.8)));Random r=new Random(seed);Vec3[] pts=new Vec3[segments+1];pts[0]=from;pts[segments]=to;
        double jitter=Math.min(.34,.055+len*.010);
        for(int i=1;i<segments;i++){double t=i/(double)segments,taper=Math.sin(Math.PI*t);pts[i]=from.add(delta.scale(t)).add(u.scale((r.nextDouble()*2-1)*jitter*taper)).add(v.scale((r.nextDouble()*2-1)*jitter*.72*taper));}
        for(int i=0;i<segments;i++)lightningSegment(vc,cam,pts[i],pts[i+1],width);
        int count=Math.min(4,Math.max(0,branches));
        for(int k=0;k<count;k++){int idx=1+r.nextInt(Math.max(1,segments-1));Vec3 root=pts[Math.min(segments-1,idx)];double side=r.nextBoolean()?1:-1;double l=Math.min(1.25,Math.max(.28,len*(.055+r.nextDouble()*.055)));Vec3 bd=dir.scale(.3).add(u.scale(side*(.65+r.nextDouble()*.4))).add(v.scale((r.nextDouble()-.25)*.5)).normalize();Vec3 end=root.add(bd.scale(l));Vec3 mid=root.lerp(end,.48).add((r.nextDouble()-.5)*.12,(r.nextDouble()-.5)*.10,(r.nextDouble()-.5)*.12);lightningSegment(vc,cam,root,mid,width*.48);lightningSegment(vc,cam,mid,end,width*.30);}
    }

    /** Sustained lightning is nearly stable; pressure pulses travel along one channel instead of rerolling the whole bolt each frame. */
    private static void renderLightningBeam(VertexConsumer vc,Vec3 cam,Vec3 from,Vec3 to,Effect e,double time){
        Vec3 delta=to.subtract(from);double len=delta.length();if(len<.03)return;Vec3 dir=delta.scale(1/len),u=orthogonal(dir),v=dir.cross(u).normalize();
        int segments=Math.min(64,Math.max(10,(int)Math.ceil(len*2.0)));Random fixed=new Random(e.id^0x4C49474854424541L);Vec3[] pts=new Vec3[segments+1];pts[0]=from;pts[segments]=to;
        double base=.055+Math.min(.055,Math.log1p(Math.max(1,e.primary*25))*.011),jitter=Math.min(.075,.018+len*.0022);
        for(int i=1;i<segments;i++){double t=i/(double)segments,taper=Math.sin(Math.PI*t),n1=fixed.nextDouble()*2-1,n2=fixed.nextDouble()*2-1,drift=Math.sin(time*.10+i*.47)*.12;pts[i]=from.add(delta.scale(t)).add(u.scale((n1+drift)*jitter*taper)).add(v.scale(n2*jitter*.62*taper));}
        double packet=((e.age%18)+.35)/18.0;
        for(int i=0;i<segments;i++){double t=(i+.5)/(double)segments,dist=Math.abs(t-packet),boost=Math.max(0,1-dist/.11),wave=.88+.12*Math.sin(time*.42-t*12);lightningBeamSegment(vc,cam,pts[i],pts[i+1],base*wave*(1+.48*boost),boost);}
    }

    private static void lightningSegment(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,double width){
        Vec3 dir=b.subtract(a);if(dir.lengthSqr()<1e-8)return;dir=dir.normalize();Vec3 side=sideForSegment(a,b,cam),cross=dir.cross(side).normalize();
        addRibbon(vc,cam,a,b,side,width*2.4,0x4899ff,75);addRibbon(vc,cam,a,b,cross,width*2.0,0x58abff,62);addRibbon(vc,cam,a,b,side,width,0xdcf4ff,235);addRibbon(vc,cam,a,b,cross,width*.82,0xffffff,250);
    }
    private static void lightningBeamSegment(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,double width,double pulse){
        Vec3 dir=b.subtract(a);if(dir.lengthSqr()<1e-8)return;dir=dir.normalize();Vec3 side=sideForSegment(a,b,cam),cross=dir.cross(side).normalize();int glow=(int)Math.min(118,72+pulse*34),core=(int)Math.min(255,228+pulse*27);
        addRibbon(vc,cam,a,b,side,width*2.25,0x4291ff,glow);addRibbon(vc,cam,a,b,cross,width*1.85,0x58b1ff,Math.max(52,glow-10));addRibbon(vc,cam,a,b,side,width*.86,0xdaf4ff,core);addRibbon(vc,cam,a,b,cross,width*.62,0xffffff,255);
    }

    private static boolean hasElement(Effect e, Element element){return (e.elementMask & (1 << element.ordinal())) != 0;}
    private static int elementCount(Effect e){return Integer.bitCount(e.elementMask);}

    private static Vec3 orthogonal(Vec3 dir){Vec3 ref=Math.abs(dir.y)<.88?new Vec3(0,1,0):new Vec3(1,0,0);Vec3 o=dir.cross(ref);return o.lengthSqr()<1e-8?new Vec3(1,0,0):o.normalize();}
    private static Vec3 sideForSegment(Vec3 a,Vec3 b,Vec3 cam){Vec3 d=b.subtract(a);if(d.lengthSqr()<1e-8)return new Vec3(0,1,0);d=d.normalize();Vec3 s=d.cross(cam.subtract(a.lerp(b,.5)));if(s.lengthSqr()<1e-8)s=d.cross(new Vec3(0,1,0));return s.lengthSqr()<1e-8?new Vec3(1,0,0):s.normalize();}

    private static List<Integer> palette(Effect e){
        List<Integer> colors=new ArrayList<>(); Element[] all=Element.values();
        Element only=null; int count=0;
        for(int i=0;i<all.length;i++) if((e.elementMask&(1<<i))!=0){colors.add(all[i].color()); only=all[i]; count++;}
        // A single element still needs visual depth: use its authored fade/highlight colour as a
        // second layer. Woven spells keep one primary colour per spoken element instead.
        if(count==1 && only!=null && only.fadeColor()!=only.color()) colors.add(only.fadeColor());
        if(colors.isEmpty())colors.add(0xd9f6ff); return colors;
    }
    private static int color(Effect e,int layer){List<Integer> p=palette(e);return p.get(Math.floorMod(layer,p.size()));}
    private static int brighten(int rgb,double factor){int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;r=(int)Math.min(255,r+(255-r)*factor);g=(int)Math.min(255,g+(255-g)*factor);b=(int)Math.min(255,b+(255-b)*factor);return(r<<16)|(g<<8)|b;}
    private static void addPaletteRibbon(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,Vec3 side,double width,Effect e,int alpha,int layer){int c=layer>=2?brighten(color(e,layer),.55):color(e,layer);addRibbon(vc,cam,a,b,side,width,c,alpha);}
    private static void addPaletteTaper(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,Vec3 oa,Vec3 ob,Effect e,int alpha,int layer){int c=layer>=2?brighten(color(e,layer),.6):color(e,layer);addQuad(vc,cam,a.add(oa),a.subtract(oa),b.subtract(ob),b.add(ob),c,alpha);}
    private static void addPaletteDiamond(VertexConsumer vc,Vec3 cam,Vec3 c,Vec3 u,Vec3 v,Effect e,int alpha,int layer){addQuad(vc,cam,c.add(u),c.add(v),c.subtract(u),c.subtract(v),layer>0?brighten(color(e,layer),.45):color(e,layer),alpha);}
    private static void addColoredQuad(VertexConsumer vc,Vec3 cam,Vec3 p1,Vec3 p2,Vec3 p3,Vec3 p4,Effect e,int alpha){addQuad(vc,cam,p1,p2,p3,p4,color(e,1),alpha);}
    private static void addRibbon(VertexConsumer vc,Vec3 cam,Vec3 a,Vec3 b,Vec3 side,double width,int rgb,int alpha){Vec3 off=side.normalize().scale(width);addQuad(vc,cam,a.add(off),a.subtract(off),b.subtract(off),b.add(off),rgb,alpha);}
    private static void addQuad(VertexConsumer vc,Vec3 cam,Vec3 p1,Vec3 p2,Vec3 p3,Vec3 p4,int rgb,int a){int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;vertex(vc,p1.subtract(cam),r,g,b,a);vertex(vc,p2.subtract(cam),r,g,b,a);vertex(vc,p3.subtract(cam),r,g,b,a);vertex(vc,p4.subtract(cam),r,g,b,a);}
    private static void vertex(VertexConsumer vc,Vec3 p,int r,int g,int b,int a){vc.addVertex((float)p.x,(float)p.y,(float)p.z).setColor(r,g,b,a);}
}
