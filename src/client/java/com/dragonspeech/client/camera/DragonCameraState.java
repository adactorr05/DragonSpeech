package com.dragonspeech.client.camera;

import net.minecraft.util.Mth;

/**
 * Camera roll (banking) while riding a dragon in flight, ported from an
 * uploaded reference mod's own CameraLeanData per explicit direction
 * ("follow the mechanics that mod uses as much as possible").
 *
 * SIMPLIFIED per explicit direction - the reference mod also computes
 * positional lean offsets (leanX/Y/Z) for a seat-bone-anchored camera
 * position, which depends on the dragon's 3D model having a dedicated
 * rider-anchor bone (their "passengerBone" - see the model/animation
 * conversation this was discussed in). This dragon's model doesn't have
 * an equivalent yet, so only the ROLL itself (banking rotation, not
 * position) is implemented here - purely yaw-rate-based, not dependent
 * on any model/bone data at all, so it works today regardless of the
 * model situation.
 *
 * REVISIT ONCE A MODEL BONE EXISTS: if/when this dragon's model is
 * rebuilt with a proper named rider-seat bone (the same concept as the
 * reference mod's passengerBone), the camera could be upgraded to pivot
 * around that bone's actual real-time position/rotation (including the
 * positional lean offsets this class currently skips) for a much more
 * connected, "sitting on the dragon" feel, rather than the simpler
 * eye-height-based position vanilla riding already uses. That upgrade
 * is real future work, not something broken today - the current
 * roll-only version is a deliberate, complete simplification, not a
 * partial implementation.
 */
public final class DragonCameraState {

    private static final double SMOOTHING = 0.15;

    private static float currentRoll = 0.0f;
    private static float targetRoll = 0.0f;
    private static float lastYaw = 0.0f;
    private static boolean hasLastYaw = false;

    private DragonCameraState() {}

    /** Called once per client tick while riding a flying dragon - see DragonSpeechClient's tick handler. */
    public static void updateTarget(float currentYaw) {
        float yawSpeed;
        if (!hasLastYaw) {
            yawSpeed = 0.0f;
            hasLastYaw = true;
        } else {
            yawSpeed = Mth.wrapDegrees(currentYaw - lastYaw);
        }
        lastYaw = currentYaw;

        // Same shape as the reference mod's own targetCameraTilt -
        // banking scales with how fast you're actually turning, capped
        // at a reasonable maximum lean.
        // Config GUI (Client tab) "Dragon Camera Roll" - a straight multiplier on top of the
        // computed lean; 0 disables banking entirely (helps with motion sickness).
        targetRoll = Mth.clamp(yawSpeed * -2.0f, -12.0f, 12.0f)
                * com.dragonspeech.client.config.DragonSpeechClientConfig.dragonCameraRollIntensity();
    }

    /** Called every client tick regardless of riding state, so roll smoothly returns to 0 after dismounting rather than snapping. */
    public static void tick() {
        currentRoll = (float) Mth.lerp(SMOOTHING, currentRoll, targetRoll);
    }

    public static void reset() {
        currentRoll = 0.0f;
        targetRoll = 0.0f;
        hasLastYaw = false;
    }

    public static float getCurrentRoll() {
        return currentRoll;
    }
}