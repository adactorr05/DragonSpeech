package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import com.dragonspeech.engine.Element;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Shoves entities away from the caster. Backs both the Water domain's flow
 * ladder (vatnhrer / streyma / flodbinda) and the Force domain's exert
 * ladder (thrysta / thrystbinda) - two different domains sharing one
 * underlying kinetic primitive, which is intentional (domains are flavor;
 * handlers are the actual mechanic).
 *
 * VERSION-RISK NOTE: Entity.push(x,y,z) and the hurtMarked field are
 * long-standing Mojang-mapped names for "add to velocity" and "flag this
 * entity's velocity to sync to its client." If either doesn't compile,
 * check Entity's decompiled source for the current equivalents.
 *
 * "til" (toward): with no other direction word spoken, "til" flips the
 * default away-from-caster shove into a pull TOWARD the caster instead -
 * "thrystbinda med thyngdarafl til" pulls a target to you, the same verb
 * that (with "aftana" instead) flings them away. See apply() below.
 */
public class PushEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        6,      // max targets per cast
        2.0f,   // max push strength per target
        20f,    // max range in blocks
        Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("push");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        // Mass and opposed motion price in per target, weighted against
        // the direction actually spoken (or away-from-caster by default).
        Vec3 spoken = KineticDirections.combined(invocation);
        Vec3 casterPos = invocation.caster().position();
        boolean usesGravity = invocation.composition().words().stream()
            .anyMatch(w -> "thyngdarafl".equals(w.trueName()));
        float gravityBoost = usesGravity ? 2.5f : 1.0f;
        float total = 0f;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity)) {
                Vec3 intent = spoken != null ? spoken
                    : entity.position().subtract(casterPos).normalize();
                total += 3.0f * KineticCost.weightFor(entity, intent) * gravityBoost;
            }
        }
        return Math.max(total, 3.0f);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        double strength = 0.6 + (invocation.modifierMagnitudeSum() * 0.4);
        strength = Math.max(0.1, Math.min(strength, CAPS.maxMagnitudePerTarget()));

        // "thyngdarafl" (gravity) named as the force behind the shove
        // isn't just flavor here - naming gravity itself as the medium is
        // supposed to hit noticeably harder than a bare-handed shove, so
        // this multiplies the FINAL impulse, after the normal strength
        // cap above already applied. That's deliberate: the strength cap
        // bounds ordinary kinetic force, but "using gravity" is a
        // categorically different, more forceful mechanism, the same way
        // a heavier weapon material hits harder on top of its base damage
        // in the hurled-weapon system - it isn't meant to be capped by
        // the same ceiling as a plain push.
        boolean usesGravity = invocation.composition().words().stream()
            .anyMatch(w -> "thyngdarafl".equals(w.trueName()));
        double gravityBoost = usesGravity ? 2.5 : 1.0;

        Vec3 casterPos = invocation.caster().position();
        Vec3 spoken = KineticDirections.combined(invocation);
        boolean usesWind = invocation.composition().words().stream()
            .anyMatch(w -> w.element().filter(e -> e == Element.WIND).isPresent());
        // "til" (toward): only meaningful for the DEFAULT direction,
        // since it's target-relative (which way is "toward the caster"
        // depends on where the target actually is) rather than a fixed
        // vector KineticDirections' caster-only math could produce -
        // that's why this is handled here rather than in
        // KineticDirections itself. An explicitly spoken up/down/
        // forward/back still wins if both are somehow spoken together,
        // same "the sentence named its directions - obey them exactly"
        // rule the rest of this handler already follows.
        boolean pullToward = invocation.composition().directionTags().contains("toward_caster");
        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity)) {
                Vec3 direction;
                double lift;
                if (spoken != null) {
                    // The sentence named its directions - obey them exactly.
                    direction = spoken;
                    lift = spoken.y * strength;
                } else {
                    Vec3 away = entity.position().subtract(casterPos);
                    Vec3 relative = pullToward ? away.scale(-1) : away;
                    direction = relative.lengthSqr() > 0.0001 ? relative.normalize() : new Vec3(0, 0, 1);
                    lift = pullToward ? 0.1 : 0.2;
                }
                entity.push(direction.x * strength * gravityBoost, lift * gravityBoost, direction.z * strength * gravityBoost);
                entity.hurtMarked = true; // ensures the velocity change is actually sent to the client
                if (usesGravity && entity.level() instanceof ServerLevel serverLevel) {
                    spawnDirectionParticles(serverLevel, entity, direction, strength * gravityBoost);
                }
                if (usesWind && entity.level() instanceof ServerLevel serverLevel) {
                    Vec3 from = invocation.caster().getEyePosition().add(direction.scale(.35));
                    Vec3 to = entity.position().add(0, entity.getBbHeight() * .5, 0);
                    SpellBodyVfx.emit(serverLevel, invocation.caster(), SpellBodyVfxType.GALE, List.of(Element.WIND),
                        from, to, (float)(.8 + strength * 1.4), 0f, 10);
                }
                affected++;
            }
        }

        return EffectResult.success(affected, "Unseen force answers the word.");
    }

    /**
     * Purple-gray motes streaming around the target in the direction it
     * was actually flung/pulled - only shown for gravity-boosted pushes
     * (a plain kinetic shove doesn't get this visual). Same custom
     * particle GravityScaleEffectHandler uses; see GravityParticle.
     */
    private static void spawnDirectionParticles(ServerLevel level, Entity target, Vec3 direction, double strength) {
        var random = level.getRandom();
        float intensity = (float) Math.max(0.3, Math.min(3.0, strength));

        for (int i = 0; i < 10; i++) {
            double offsetX = (random.nextDouble() - 0.5) * target.getBbWidth();
            double offsetZ = (random.nextDouble() - 0.5) * target.getBbWidth();
            double offsetY = random.nextDouble() * target.getBbHeight();
            Vec3 vel = direction.scale(0.1 * intensity)
                .add((random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02);

            SpellFx.of(DragonSpeechParticles.GRAVITY)
                .pos(target.getX() + offsetX, target.getY() + offsetY, target.getZ() + offsetZ)
                .vel(vel)
                .time(16)
                .spawn(level);
        }
    }
}
