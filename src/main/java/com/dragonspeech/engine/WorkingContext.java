package com.dragonspeech.engine;

import com.dragonspeech.effect.EffectInvocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Everything a FormEngine needs, resolved once by ElementalWorkingHandler
 * from the spoken sentence:
 *
 * - elements:  from the verb's own element and/or element nouns (eldr,
 *              elding, is...). More than one only when samvefja was spoken.
 * - power:     verb precision + power modifiers (mikla/litla/ofsa),
 *              already clamped to the handler's cap.
 * - count:     from quantity words (margfalt/tvefalt -> repeat_count).
 * - targeting: leitbinda (homing) / kedjubinda (chain), if spoken.
 * - direction: combined spoken direction words, falling back to the
 *              caster's gaze.
 * - scopeRadius: from the scope word (naerum/umhverf/viddum), 0 if none.
 * - anchors:   the resolved targets - an entity, a block position, or a
 *              free direction cast (marklaust), each reduced to a point
 *              plus the entity that point belongs to (if any).
 */
public record WorkingContext(
    ServerPlayer caster,
    ServerLevel level,
    List<Element> elements,
    EffectInvocation invocation,
    float power,
    int count,
    Optional<TargetingStyle> targeting,
    Vec3 direction,
    float scopeRadius,
    List<Anchor> anchors
) {

    /** A resolved point the working is aimed at, plus the entity standing there (if the target was an entity). */
    public record Anchor(Vec3 pos, Entity entity) {
        public static Anchor of(Entity entity) {
            return new Anchor(entity.position().add(0, entity.getBbHeight() * 0.5, 0), entity);
        }

        public static Anchor of(Vec3 pos) {
            return new Anchor(pos, null);
        }
    }

    /**
     * Where a hand-cast working leaves the caster.  Gameplay raycasts use the same approximate
     * right-hand origin as the visual layer instead of beginning inside the camera.  The client
     * renderer refines this anchor to the actual first/third-person hand position, but keeping the
     * server origin here prevents old particle helpers and hit paths from visibly starting at the eye.
     */
    public Vec3 origin() {
        Vec3 forward = caster.getLookAngle();
        if (forward.lengthSqr() < 1.0e-8) forward = direction;
        if (forward.lengthSqr() < 1.0e-8) forward = new Vec3(0, 0, 1);
        forward = forward.normalize();

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0e-6) {
            double yaw = Math.toRadians(caster.getYRot());
            Vec3 flatForward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            right = flatForward.cross(worldUp);
        }
        right = right.normalize();
        Vec3 up = right.cross(forward);
        if (up.lengthSqr() < 1.0e-6) up = worldUp; else up = up.normalize();

        return caster.getEyePosition()
            .add(forward.scale(0.62))
            .add(right.scale(0.40))
            .subtract(up.scale(0.32));
    }

    /** The true camera/gaze origin, used only when deciding what point the caster is looking at. */
    public Vec3 gazeOrigin() {
        return caster.getEyePosition();
    }

    /** A far point on the spoken/look direction, useful for making a hand-origin path converge on the crosshair. */
    public Vec3 gazePoint(double range) {
        return gazeOrigin().add(direction.normalize().scale(range));
    }

    /** Direction from the hand to the point under the caster's gaze at the supplied range. */
    public Vec3 handAim(double range) {
        Vec3 aim = gazePoint(range).subtract(origin());
        return aim.lengthSqr() < 1.0e-8 ? direction.normalize() : aim.normalize();
    }

    /** Applies every woven element to a living target. Weaving splits, not stacks: each element lands at reduced power so two elements never simply double a working for free. */
    public void hitEntity(Entity target, float powerScale) {
        if (target instanceof LivingEntity living && invocation.composition().occurrencesOf("sprengja") > 0
                && com.dragonspeech.ward.WardInterception.blocks(living, com.dragonspeech.ward.WardType.EXPLOSION,
                    Math.max(1f, power * Math.max(0.25f, powerScale)))) return;
        float split = elements.size() <= 1 ? 1f : (float) (1.0 / Math.sqrt(elements.size()));
        float focus = WorkingModifiers.compressionPowerScale(this);
        for (Element element : elements) {
            element.hitEntity(caster, target, power * powerScale * focus * split);
        }
        WorkingModifiers.applyMomentum(this, target, powerScale);
    }

    /** Targeting words compose rather than competing for one enum slot. */
    public boolean hasTargeting(TargetingStyle style) {
        return invocation.composition().hasTargeting(style);
    }

    /** Applies every woven element's block mark at a position. */
    public void hitBlock(net.minecraft.core.BlockPos pos, float powerScale) {
        for (Element element : elements) {
            element.hitBlock(level, pos, power * powerScale);
        }
    }

    /** Trail fx for every woven element (they overlap into a blended trail). */
    public void trailFx(Vec3 from, Vec3 to) {
        for (Element element : elements) {
            element.trailFx(level, from, to);
        }
    }

    /** Impact fx for every woven element. */
    public void impactFx(Vec3 pos) {
        for (Element element : elements) {
            element.impactFx(level, pos);
        }
    }

    /** Living entities within `radius` of `center`, nearest first, excluding the caster. */
    public List<LivingEntity> livingWithin(Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(radius),
                e -> e != caster && e.isAlive() && !e.isSpectator()
                    && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radius * radius)
            .stream()
            .sorted((a, b) -> Double.compare(a.distanceToSqr(center.x, center.y, center.z), b.distanceToSqr(center.x, center.y, center.z)))
            .toList();
    }
}
