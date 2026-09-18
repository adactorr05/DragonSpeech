package com.dragonspeech.client.dragon.vfx;

import com.dragonspeech.dragon.DragonEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Adapted from Saints Dragons' own DragonDiveEffectIntensity - same
 * real thresholds and formula shape (overall speed AND downward speed
 * both need to clear their own start/full range before the trail
 * appears, multiplied together so both matter), computed from actual
 * velocity/position-delta rather than gated on a specific key, so it
 * reflects genuine dive speed regardless of how that speed was
 * reached.
 *
 * One real simplification, not a silent gap: Saints' own version also
 * has a "hold intensity" that keeps the trail visible briefly after
 * pulling out of a dive, driven by a dive-boost-momentum counter their
 * own throttle system tracks. This project doesn't have that
 * momentum system, so that part is left out rather than faked with an
 * unrelated substitute - the trail here fades out as soon as speed
 * drops below the threshold, with no extra hang-time.
 */
public final class DragonDiveEffectIntensity {
    private static final double DIVE_START_SPEED = 1.20D;
    private static final double DIVE_FULL_SPEED = 4.00D;
    private static final double DIVE_START_DOWNWARD_SPEED = 0.3D;
    private static final double DIVE_FULL_DOWNWARD_SPEED = 1.35D;

    private DragonDiveEffectIntensity() {
    }

    public static float get(DragonEntity dragon) {
        if (!dragon.isFlying() || dragon.isInWaterOrBubble()) {
            return 0.0F;
        }

        Vec3 velocity = dragon.getDeltaMovement();
        Vec3 positionDelta = new Vec3(
                dragon.getX() - dragon.xo,
                dragon.getY() - dragon.yo,
                dragon.getZ() - dragon.zo
        );
        double speed = Math.max(velocity.length(), positionDelta.length());
        double downwardSpeed = Math.max(-velocity.y, -positionDelta.y);
        double speedFactor = normalize(speed, DIVE_START_SPEED, DIVE_FULL_SPEED);

        if (downwardSpeed <= DIVE_START_DOWNWARD_SPEED) {
            return 0.0F;
        }
        double downwardFactor = normalize(downwardSpeed, DIVE_START_DOWNWARD_SPEED, DIVE_FULL_DOWNWARD_SPEED);
        return (float) (speedFactor * downwardFactor);
    }

    private static double normalize(double value, double start, double end) {
        return Mth.clamp((value - start) / (end - start), 0.0D, 1.0D);
    }
}
