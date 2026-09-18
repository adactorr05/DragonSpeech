package com.dragonspeech.engine;

import com.dragonspeech.effect.EffectResult;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * CLOUD (skyja): a lingering hanging mist. Behavior reference: EBW's
 * lingering cloud spells (Poison Cloud / Mind Fog / Decay). Implemented
 * on vanilla's AreaEffectCloud so lingering, per-entity reapplication and
 * expiry are all battle-tested vanilla behavior - the element picks the
 * cloud's effect, colour, and particle.
 */
public class CloudEngine implements FormEngine {

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();

        Vec3 center;
        if (!ctx.anchors().isEmpty() && ctx.anchors().get(0).entity() != null) {
            center = ctx.anchors().get(0).entity().position();
        } else if (!ctx.anchors().isEmpty()) {
            center = Strikes.ray(ctx.caster(), ctx.gazeOrigin(), ctx.direction(), 20.0).pos();
        } else {
            center = ctx.caster().position();
        }

        float radius = (float)((ctx.scopeRadius() > 0 ? Math.min(ctx.scopeRadius(), 6f) : 3f) * WorkingModifiers.areaScale(ctx));
        int duration = Math.round(100 + ctx.power() * 30); // 5s base, up to ~20s+

        int placed = 0;
        for (Element element : ctx.elements()) {
            AreaEffectCloud cloud = new AreaEffectCloud(level, center.x, center.y, center.z);
            cloud.setOwner(ctx.caster());
            cloud.setRadius(radius);
            cloud.setDuration(duration);
            cloud.setRadiusOnUse(-0.3f);
            cloud.setWaitTime(10);
            cloud.setParticle(element.trailParticle());

            switch (element) {
                case POISON -> cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                case ICE, WATER -> cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                case FIRE -> cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0));
                case SHADOW -> cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                case DEATH -> cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                case LIFE -> cloud.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
                case LIGHT -> cloud.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                case EARTH -> cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                case LIGHTNING, FORCE -> cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                case WIND -> cloud.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
                case VOID -> {
                    cloud.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                    cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                }
            }

            level.addFreshEntity(cloud);
            placed++;
        }

        SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.CLOUD, ctx.elements(), center.add(0,.5,0), center,
            radius, 1.2f, duration);
        SpellFx.flash(level, Strikes.primaryColor(ctx), center.add(0, 0.6, 0));
        return EffectResult.success(placed, "The word hangs in the air, and a mist gathers around it.");
    }
}

/**
 * AURA (umljomi): a steady field around the caster. Behavior reference:
 * EBW Healing Aura / Static Aura / Growth Aura. LIFE turns the field
 * inward - it mends the caster and every player near them; every other
 * element turns it outward, striking hostile marks in the field at
 * reduced power. The buff swirl particle rises from everything touched.
 */
class AuraEngine implements FormEngine {

    private static final float AURA_POWER_SCALE = 0.6f;

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();
        double radius = (ctx.scopeRadius() > 0 ? ctx.scopeRadius() : 4.0) * WorkingModifiers.areaScale(ctx);
        Vec3 center = ctx.caster().position();
        int color = Strikes.primaryColor(ctx);

        boolean mending = ctx.elements().contains(Element.LIFE);
        int touched = 0;
        SpellBodyVfx.emit(level, ctx.caster(), SpellBodyVfxType.AURA, ctx.elements(), center, center,
            (float)radius, 0f, Math.round(40 + ctx.power() * 8));

        if (mending) {
            int duration = Math.round(80 + ctx.power() * 30);
            ctx.caster().addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0));
            SpellFx.buffSwirl(level, color, ctx.caster());
            touched++;

            for (LivingEntity ally : ctx.livingWithin(center.add(0, 1, 0), radius)) {
                if (ally instanceof net.minecraft.server.level.ServerPlayer) {
                    ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0));
                    SpellFx.buffSwirl(level, color, ally);
                    touched++;
                }
            }
        } else {
            for (LivingEntity target : ctx.livingWithin(center.add(0, 1, 0), radius)) {
                if (target instanceof net.minecraft.server.level.ServerPlayer) {
                    continue; // an outward aura doesn't lash other players by default
                }
                ctx.hitEntity(target, AURA_POWER_SCALE);
                SpellFx.buffSwirl(level, color, target);
                touched++;
            }
        }

        // The field itself: a slow spiral of the element's motes around the caster.
        for (Element element : ctx.elements()) {
            SpellFx.spiral(level, element.trailParticle(), element.color(), element.fadeColor(), ctx.caster(), radius * 0.6);
        }

        return EffectResult.success(touched, mending
            ? "The word settles around you like warmth, and wounds begin to close."
            : "The word settles around you, and the field bites at everything that comes near.");
    }
}

/**
 * SIGIL (ristmark): a mark placed on the ground that waits for a victim.
 * Behavior reference: EBW Fire Sigil / Frost Sigil / Lightning Sigil.
 * The mark is placed where the gaze lands (or under a bound target),
 * glows quietly via SigilManager's idle fx, and detonates its woven
 * elements at amplified power on the first living thing (not the caster)
 * to step onto it.
 */
class SigilEngine implements FormEngine {

    @Override
    public EffectResult run(WorkingContext ctx) {
        ServerLevel level = ctx.level();

        Vec3 aimPoint;
        if (!ctx.anchors().isEmpty() && ctx.anchors().get(0).entity() != null) {
            aimPoint = ctx.anchors().get(0).entity().position();
        } else if (!ctx.anchors().isEmpty()) {
            aimPoint = Strikes.ray(ctx.caster(), ctx.gazeOrigin(), ctx.direction(), 20.0).pos();
        } else {
            aimPoint = ctx.caster().position();
        }
        Vec3 ground = Strikes.dropToGround(level, ctx.caster(), aimPoint.add(0, 1, 0), 8);

        int placed = 0;
        for (int i = 0; i < ctx.count(); i++) {
            Vec3 pos = ground;
            if (i > 0) {
                // margfalt scatters extra marks around the first.
                var random = level.getRandom();
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 1.5 + random.nextDouble() * 2.5;
                pos = Strikes.dropToGround(level, ctx.caster(),
                    ground.add(Math.cos(angle) * distance, 2, Math.sin(angle) * distance), 8);
            }
            SigilManager.place(level, pos, ctx.elements(), ctx.power(), ctx.caster());
            placed++;
        }

        return EffectResult.success(placed, "The word sinks into the ground and waits, patient as stone.");
    }
}

/** Package-private construction point mirroring ProjectileEngines. */
final class FieldEngines {
    static final FormEngine BURST = new BurstEngine();
    static final FormEngine RING = new RingEngine();
    static final FormEngine RAIN = new RainEngine();
    static final FormEngine CLOUD = new CloudEngine();
    static final FormEngine AURA = new AuraEngine();
    static final FormEngine SIGIL = new SigilEngine();

    private FieldEngines() {}
}
