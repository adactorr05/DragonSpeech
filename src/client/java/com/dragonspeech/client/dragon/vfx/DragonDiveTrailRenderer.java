package com.dragonspeech.client.dragon.vfx;

import com.dragonspeech.dragon.DragonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adapted from Saints Dragons' own DragonDiveTrailRenderer - same
 * ring-buffer-per-wingtip-plus-tail structure and intensity-gated
 * recording, but computing approximate tip positions from entity-
 * relative offsets rotated by yaw each frame, rather than their own
 * true per-bone world-position tracking (which reads actual animated
 * bone transforms during render - technically more accurate, but a
 * much larger, riskier undertaking to build correctly than this
 * project's rendering code has generally allowed for this session).
 * Real trade-off, not a silent one: this trail follows the dragon's
 * overall body orientation smoothly, but won't reflect the individual
 * flap-by-flap motion of the actual wingtip bone the way Saints' own
 * does.
 *
 * Offset magnitudes are computed from this project's own real model
 * geometry (the same wing-length figures verified several rounds ago
 * for the camera zoom fix - ~9.1 blocks per wing from shoulder to tip)
 * rather than guessed fresh.
 */
public final class DragonDiveTrailRenderer {
    private static final int TRAIL_LENGTH = 18;
    private static final float TRAIL_ALPHA = 1.0F;
    private static final Map<DragonEntity, TrailTriple> TRAILS = new WeakHashMap<>();

    // Block offsets (at scale 1.0) for each tip, relative to the
    // entity's own position - X is sideways (mirrored for left/right
    // wing), Y is a slight downward droop matching the wing's own
    // resting pivot height, Z follows this project's own confirmed
    // passenger-attachment convention (positive = toward the tail).
    private static final Vec3 RIGHT_WING_TIP_OFFSET = new Vec3(-9.1, 0.3, 0.5);
    private static final Vec3 LEFT_WING_TIP_OFFSET = new Vec3(9.1, 0.3, 0.5);
    private static final Vec3 TAIL_TIP_OFFSET = new Vec3(0, 0, 4.0);

    private DragonDiveTrailRenderer() {
    }

    public static void render(DragonEntity dragon, MultiBufferSource bufferSource, PoseStack.Pose pose, float partialTick) {
        TrailTriple trails = TRAILS.computeIfAbsent(dragon, ignored -> new TrailTriple());
        float intensity = DragonDiveEffectIntensity.get(dragon);

        if (trails.lastRecordedTick != dragon.tickCount) {
            trails.lastRecordedTick = dragon.tickCount;
            if (intensity > 0.0F) {
                float scale = dragon.getAgeScale();
                // FIX: avoided an unconfirmed field (yBodyRotO) - not
                // referenced anywhere else in this codebase, and this
                // is already a visual approximation rather than true
                // bone tracking, so skipping partial-tick interpolation
                // for the yaw here specifically is a reasonable,
                // lower-risk simplification rather than a guess at an
                // API that might not exist.
                double yawRad = Math.toRadians(-dragon.yBodyRot);
                Vec3 base = dragon.getPosition(partialTick);

                trails.left.add(base.add(rotateY(LEFT_WING_TIP_OFFSET.scale(scale), yawRad)), TRAIL_ALPHA * intensity);
                trails.right.add(base.add(rotateY(RIGHT_WING_TIP_OFFSET.scale(scale), yawRad)), TRAIL_ALPHA * intensity);
                trails.tail.add(base.add(rotateY(TAIL_TIP_OFFSET.scale(scale), yawRad)), TRAIL_ALPHA * intensity);
            } else {
                trails.left.decay();
                trails.right.decay();
                trails.tail.decay();
            }
        }

        DragonWingTrailRenderer.render(trails.left, bufferSource, pose);
        DragonWingTrailRenderer.render(trails.right, bufferSource, pose);
        DragonWingTrailRenderer.render(trails.tail, bufferSource, pose);
    }

    private static Vec3 rotateY(Vec3 v, double yawRad) {
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    private static final class TrailTriple {
        private final DragonWingTrail left = new DragonWingTrail(TRAIL_LENGTH);
        private final DragonWingTrail right = new DragonWingTrail(TRAIL_LENGTH);
        private final DragonWingTrail tail = new DragonWingTrail(TRAIL_LENGTH);
        private int lastRecordedTick = -1;
    }
}
