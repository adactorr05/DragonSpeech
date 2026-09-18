package com.dragonspeech.engine;

import com.dragonspeech.effect.EffectResult;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import com.dragonspeech.entity.MagicBarrierEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * New reusable forms inspired by Dragon Flux's strongest ability silhouettes. These are grammar
 * primitives, never named abilities: the spoken element(s) determine what a lance/tether/etc. does.
 */
final class LanceEngine implements FormEngine {
    private static final double RANGE = 30.0;

    @Override public EffectResult run(WorkingContext ctx) {
        int struck=0;
        for (WorkingContext.Anchor anchor : ctx.anchors()) {
            for (int shot=0; shot<ctx.count(); shot++) {
                Vec3 origin=ctx.origin();
                Vec3 aim=anchor.entity()!=null ? anchor.pos().subtract(origin) : ctx.handAim(RANGE);
                if (aim.lengthSqr()<1e-5) aim=ctx.direction();
                if (shot>0) {
                    var r=ctx.level().getRandom();
                    aim=aim.normalize().add((r.nextDouble()-.5)*.07,(r.nextDouble()-.5)*.07,(r.nextDouble()-.5)*.07);
                }
                aim=aim.normalize();
                Strikes.StrikeHit wall=Strikes.ray(ctx.caster(),origin,aim,RANGE);
                Vec3 end=wall.blockPos()!=null?wall.pos():origin.add(aim.scale(RANGE));
                SpellCollisionManager.PathResult collision = SpellCollisionManager.resolvePath(
                    ctx, origin, end, .18f, ctx.power() * 1.05f, 9);
                end = collision.end();
                double len=origin.distanceTo(end);

                // A lance is a narrow piercing form rather than a broad beam.
                double lanceWidth=.42*WorkingModifiers.areaScale(ctx);
                AABB sweep=new AABB(origin,end).inflate(lanceWidth);
                for (LivingEntity target : ctx.level().getEntitiesOfClass(LivingEntity.class,sweep,
                    e->e!=ctx.caster()&&e.isAlive()&&!e.isSpectator())) {
                    Vec3 center=target.position().add(0,target.getBbHeight()*.5,0);
                    Vec3 to=center.subtract(origin); double along=to.dot(aim);
                    if(along<0||along>len||to.subtract(aim.scale(along)).length()>.62*WorkingModifiers.areaScale(ctx)) continue;
                    if (!collision.blocked()) {
                        ctx.hitEntity(target,1.05f * collision.powerScale()); ctx.impactFx(center); struck++;
                    }
                }
                if (!collision.blocked() && wall.blockPos()!=null) ctx.hitBlock(wall.blockPos(),1.05f * collision.powerScale());
                if (collision.blocked()) { ctx.impactFx(end); struck++; }
                SpellBodyVfx.emit(ctx.level(),ctx.caster(),SpellBodyVfxType.LANCE,ctx.elements(),origin,end,
                    .11f+ctx.power()*.012f,1.0f,10);
                MotionModifiers.emitPathDecorators(ctx,origin,end,.28f+ctx.power()*.015f,3.5f,10);
            }
        }
        return EffectResult.success(struck,"The working narrows into a piercing point and drives forward.");
    }
}

final class TetherEngine implements FormEngine {
    @Override public EffectResult run(WorkingContext ctx) {
        int touched=0;
        for (WorkingContext.Anchor anchor : ctx.anchors()) {
            Vec3 end=anchor.entity()!=null?anchor.pos():Strikes.ray(ctx.caster(),ctx.origin(),ctx.direction(),24).pos();
            boolean toward = ctx.invocation().composition().directionTags().contains("toward_caster");
            boolean away = ctx.invocation().composition().directionTags().contains("away");
            if(anchor.entity() instanceof LivingEntity target){
                // The tether itself only connects/carries the spoken element. Motion is a separate
                // semantic request: til draws the target toward the speaker, fran drives it away.
                ctx.hitEntity(target,.55f);
                if (toward || away) {
                    Vec3 motion = toward
                        ? ctx.caster().position().subtract(target.position())
                        : target.position().subtract(ctx.caster().position());
                    if(motion.lengthSqr()>1e-5){
                        motion=motion.normalize().scale(Math.min(1.15, .48+ctx.power()*.050));
                        target.push(motion.x,.08+Math.max(0,motion.y)*.18,motion.z);
                        target.hurtMarked=true;
                    }
                }
                touched++;
            } else if (toward || away) {
                // On terrain, til turns the tether into a grapple (speaker -> anchor); fran becomes
                // a tethered kick-off/repulsion. Without either direction word, the line simply holds.
                Vec3 motion = toward
                    ? end.subtract(ctx.caster().position())
                    : ctx.caster().position().subtract(end);
                if(motion.lengthSqr()>2.25){
                    motion=motion.normalize().scale(Math.min(.90, .34+ctx.power()*.035));
                    ctx.caster().push(motion.x,Math.max(.04,motion.y*.25),motion.z);
                    ctx.caster().hurtMarked=true;
                }
            }
            int vfxLife = ctx.invocation().composition().hasContinuousModifier() ? 24 : 7;
            SpellBodyVfx.emit(ctx.level(),ctx.caster(),SpellBodyVfxType.TETHER,ctx.elements(),ctx.origin(),end,
                .10f+ctx.power()*.01f,0,vfxLife);
            MotionModifiers.emitPathDecorators(ctx,ctx.origin(),end,.22f+ctx.power()*.012f,3.0f,vfxLife);
        }
        return EffectResult.success(touched,"The working holds as a flexible bond between speaker and mark.");
    }
}

final class ClawEngine implements FormEngine {
    @Override public EffectResult run(WorkingContext ctx) {
        Vec3 origin=ctx.caster().getEyePosition(); Vec3 look=ctx.direction().normalize();
        double reach=3.1+Math.min(1.4,ctx.power()*.08); int struck=0;
        for(LivingEntity target:ctx.livingWithin(ctx.caster().position().add(0,1,0),reach+1)){
            Vec3 center=target.position().add(0,target.getBbHeight()*.5,0);Vec3 to=center.subtract(origin);double d=to.length();
            if(d>.1&&d<=reach&&to.normalize().dot(look)>.35){ctx.hitEntity(target,.88f);ctx.impactFx(center);struck++;if(struck>=Math.max(1,ctx.count()))break;}
        }
        int vfxLife = ctx.invocation().composition().hasContinuousModifier() ? 24 : 9;
        SpellBodyVfx.emit(ctx.level(),ctx.caster(),SpellBodyVfxType.CLAW,ctx.elements(),ctx.caster().position(),look,
            .70f+ctx.power()*.035f,0,vfxLife);
        return EffectResult.success(struck,"The working hooks itself along your hands like talons.");
    }
}

final class SpiralEngine implements FormEngine {
    @Override public EffectResult run(WorkingContext ctx) {
        Vec3 center;
        if(!ctx.anchors().isEmpty()&&ctx.anchors().get(0).entity()!=null)center=ctx.anchors().get(0).pos();
        else if(!ctx.anchors().isEmpty())center=Strikes.ray(ctx.caster(),ctx.gazeOrigin(),ctx.direction(),20).pos();
        else center=ctx.caster().position();
        double radius=ctx.scopeRadius()>0?ctx.scopeRadius():3.2; double height=2.8+ctx.power()*.14; int struck=0;
        for(LivingEntity target:ctx.livingWithin(center.add(0,height*.35,0),radius+1)){
            Vec3 flat=new Vec3(target.getX()-center.x,0,target.getZ()-center.z); if(flat.length()>radius+0.8)continue;
            ctx.hitEntity(target,.58f);
            if(flat.lengthSqr()>1e-5){Vec3 tang=new Vec3(-flat.z,0,flat.x).normalize().scale(.20+ctx.power()*.012);target.push(tang.x,.12,tang.z);target.hurtMarked=true;}
            struck++;
        }
        int vfxLife = ctx.invocation().composition().hasContinuousModifier() ? 24 : 18;
        SpellBodyVfx.emit(ctx.level(),ctx.caster(),SpellBodyVfxType.SPIRAL,ctx.elements(),center,center.add(0,height,0),(float)radius,3.25f,vfxLife);
        return EffectResult.success(struck,"The working turns around its centre and climbs in a spiral.");
    }
}

final class ShellEngine implements FormEngine {
    @Override public EffectResult run(WorkingContext ctx) {
        Vec3 center=ctx.caster().position().add(0,ctx.caster().getBbHeight()*.5,0);
        LivingEntity wrapped=ctx.caster();
        if(!ctx.anchors().isEmpty()&&ctx.anchors().get(0).entity() instanceof LivingEntity living){wrapped=living;center=living.position().add(0,living.getBbHeight()*.5,0);}
        double radius=(ctx.scopeRadius()>0?Math.min(3.5,ctx.scopeRadius()):Math.max(.85,wrapped.getBbWidth()*.85+.55))*WorkingModifiers.areaScale(ctx);
        int touched=0;
        // "Wrap" is not silently promoted to a ward. It only carries the spoken element on contact.
        for(LivingEntity target:ctx.livingWithin(center,radius+.8)){
            if(target==wrapped)continue; double d=target.position().add(0,target.getBbHeight()*.5,0).distanceTo(center);
            if(d>=Math.max(.25,radius-.8)){ctx.hitEntity(target,.42f);touched++;}
        }
        SpellBodyVfx.emit(ctx.level(),ctx.caster(),SpellBodyVfxType.SHELL,ctx.elements(),center,center,(float)radius,
            (float)Math.max(.75,wrapped.getBbHeight()*.62),24);
        return EffectResult.success(touched,"The working wraps close around its mark.");
    }
}


/**
 * A mobile, sharpened rotating ring.  It is not a named ability: the engine is selected only
 * when the sentence literally contains kasta + hringr + sveira + hvassa.  The element(s) still
 * decide what the construct is made from, so force, fire, ice, lightning, Void, etc. all reuse
 * the same form.
 */
final class CuttingRingEngine implements FormEngine {
    private static final double RANGE = 28.0;

    @Override public EffectResult run(WorkingContext ctx) {
        Optional<Vec3> originOpt = resolveOrigin(ctx);
        if (originOpt.isEmpty()) {
            return EffectResult.failure("No barrier of yours answers as the source of the spinning ring.");
        }
        Vec3 origin = originOpt.get();
        int struck = 0;

        for (int shot = 0; shot < Math.max(1, ctx.count()); shot++) {
            Vec3 aim;
            if (!ctx.anchors().isEmpty() && ctx.anchors().get(0).entity() != null) {
                aim = ctx.anchors().get(0).pos().subtract(origin);
            } else {
                // Preserve crosshair targeting even when the construct originates from a barrier.
                Vec3 gazeEnd = ctx.gazeOrigin().add(ctx.direction().normalize().scale(RANGE));
                aim = gazeEnd.subtract(origin);
            }
            if (aim.lengthSqr() < 1.0e-6) aim = ctx.direction();
            aim = aim.normalize();

            if (shot > 0) {
                // Multiple spoken copies are distinct constructs, not coincident duplicate damage.
                var r = ctx.level().getRandom();
                aim = aim.add((r.nextDouble() - .5) * .08, (r.nextDouble() - .5) * .055,
                    (r.nextDouble() - .5) * .08).normalize();
            }

            Strikes.StrikeHit wall = Strikes.ray(ctx.caster(), origin, aim, RANGE);
            Vec3 intendedEnd = wall.blockPos() != null ? wall.pos() : origin.add(aim.scale(RANGE));
            float ringRadius = (float)Math.max(.48, Math.min(1.35,
                (.56 + ctx.power() * .035) * WorkingModifiers.areaScale(ctx)));
            float pressure = ctx.power() * 1.12f;
            SpellCollisionManager.PathResult collision = SpellCollisionManager.resolvePath(
                ctx, origin, intendedEnd, ringRadius * .46f, pressure, 12);
            Vec3 end = collision.end();

            Vec3 segment = end.subtract(origin);
            double length = segment.length();
            Vec3 dir = length > 1.0e-6 ? segment.scale(1.0 / length) : aim;
            AABB sweep = new AABB(origin, end).inflate(ringRadius + .8);
            for (LivingEntity target : ctx.level().getEntitiesOfClass(LivingEntity.class, sweep,
                    e -> e != ctx.caster() && e.isAlive() && !e.isSpectator())) {
                Vec3 center = target.position().add(0, target.getBbHeight() * .5, 0);
                Vec3 to = center.subtract(origin);
                double along = to.dot(dir);
                if (along < 0 || along > length) continue;
                double off = to.subtract(dir.scale(along)).length();
                // The ring is a disc, not a solid ball: allow its rim plus the target's body width.
                if (off > ringRadius + Math.max(.28, target.getBbWidth() * .55)) continue;
                ctx.hitEntity(target, .92f * collision.powerScale());
                ctx.impactFx(center);
                struck++;
            }

            if (!collision.blocked() && wall.blockPos() != null) {
                ctx.hitBlock(wall.blockPos(), .78f * collision.powerScale());
            } else if (collision.blocked()) {
                ctx.impactFx(end);
            }

            SpellBodyVfx.emit(ctx.level(), ctx.caster(), SpellBodyVfxType.CUTTING_RING, ctx.elements(),
                origin, end, ringRadius, .055f + ctx.power() * .0035f, 12);
        }

        return EffectResult.success(struck,
            "The ring leaves the working spinning, its sharpened edge carried along the cast path.");
    }

    /** med varn lets the same construct be routed out of an owned barrier rather than the hand. */
    private static Optional<Vec3> resolveOrigin(WorkingContext ctx) {
        boolean throughBarrier = ctx.invocation().composition().occurrencesOf("med") > 0
            && ctx.invocation().composition().occurrencesOf("varn") > 0;
        if (!throughBarrier) return Optional.of(ctx.origin());
        Optional<MagicBarrierEntity> source = MagicBarrierEntity.findBarrierInSight(ctx.level(), ctx.caster(), 28.0, true);
        if (source.isEmpty()) source = MagicBarrierEntity.findPersonalShieldFor(ctx.level(), ctx.caster().getUUID());
        if (source.isEmpty()) source = MagicBarrierEntity.findOwnedBarrierNear(ctx.level(), ctx.caster().getUUID(), ctx.caster().position(), 28.0);
        return source.map(b -> b.position().add(0, b.getBbHeight() * .25, 0));
    }
}

final class AdvancedFormEngines {
    static final FormEngine LANCE=new LanceEngine();
    static final FormEngine TETHER=new TetherEngine();
    static final FormEngine CLAW=new ClawEngine();
    static final FormEngine SPIRAL=new SpiralEngine();
    static final FormEngine SHELL=new ShellEngine();
    static final FormEngine CUTTING_RING=new CuttingRingEngine();
    private AdvancedFormEngines(){}
}
