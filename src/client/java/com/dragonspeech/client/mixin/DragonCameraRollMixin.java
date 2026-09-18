package com.dragonspeech.client.mixin;

import com.dragonspeech.client.camera.DragonCameraState;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ported from an uploaded reference mod's own CameraRollMixin per
 * explicit direction ("follow the mechanics that mod uses as much as
 * possible") - CONFIRMED, not guessed, against your actual decompiled
 * 1.21.1 Camera.java source before writing this: setup() calls
 * setRotation(float, float) exactly 3 times, in the exact same order
 * and context the reference mod's own 1.20.1 mixin targets (the normal
 * view rotation, the reversed third-person-front-view rotation inside
 * the detached-camera branch, and the sleeping-direction rotation) -
 * and setRotation() itself calls Quaternionf.rotationYXZ(yaw, pitch,
 * roll) with roll hardcoded to 0.0F, exactly where this mixin's
 * @ModifyArg injects the real value instead. This is a structural
 * match, not a version-risk guess.
 *
 * Vanilla's own Camera has no concept of roll at all outside this -
 * this is what actually makes banking during flight visible at all,
 * not just a stored number nothing reads.
 */
@Mixin(Camera.class)
public class DragonCameraRollMixin {

    @Shadow
    private Entity entity;

    @Unique
    private float dragonspeech$tempRoll = 0.0f;

    @Inject(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void dragonspeech$prepareFirstRotationRoll(
            BlockGetter level, Entity focusedEntity, boolean detached, boolean thirdPersonFront, float partialTick, CallbackInfo ci
    ) {
        this.dragonspeech$tempRoll = DragonCameraState.getCurrentRoll();
    }

    @Inject(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void dragonspeech$prepareSecondRotationRoll(
            BlockGetter level, Entity focusedEntity, boolean detached, boolean thirdPersonFront, float partialTick, CallbackInfo ci
    ) {
        // The reversed third-person-front-view rotation - inverted to match, same as the reference mod does.
        this.dragonspeech$tempRoll = -DragonCameraState.getCurrentRoll();
    }

    @Inject(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    ordinal = 2,
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void dragonspeech$prepareThirdRotationRoll(
            BlockGetter level, Entity focusedEntity, boolean detached, boolean thirdPersonFront, float partialTick, CallbackInfo ci
    ) {
        // The sleeping-direction rotation - no roll while sleeping.
        this.dragonspeech$tempRoll = 0.0f;
    }

    @ModifyArg(
            method = "setRotation",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;",
                    remap = false
            ),
            index = 2
    )
    private float dragonspeech$injectRollIntoCamera(float originalRoll) {
        float roll = this.dragonspeech$tempRoll;
        return roll != 0.0f ? roll * ((float) Math.PI / 180.0f) : originalRoll;
    }

    /**
     * "The camera isn't zooming out to view the whole dragon when
     * flying... it should be zoomed out to view the whole dragon while
     * riding it" per explicit direction.
     *
     * Confirmed against your real Camera.java source: third-person
     * distance already scales with the CAMERA-FOCUSED entity's own
     * getScale() (this.move(-this.getMaxZoom(4.0F * g), ...) where g is
     * that scale) - but the camera's focused entity while riding is
     * always the PLAYER, never the vehicle, so a rider's own scale
     * (always 1.0) is all vanilla ever accounts for. The dragon's own
     * size never factored in at all, regardless of how large it
     * actually is.
     */
    @ModifyArg(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"
            )
    )
    private float dragonspeech$zoomOutForDragon(float distance) {
        if (this.entity != null && this.entity.getVehicle() instanceof com.dragonspeech.dragon.DragonEntity) {
            // FIX: "the 3rd person camera when riding is slightly too
            // far from the dragon, even when the dragon is an ancient"
            // per explicit direction - the 7.0x computed from wingspan
            // ratio was apparently too aggressive in practice ("even
            // when ancient" rules out this being a too-small-for-the-
            // biggest-size problem specifically). Dialed back
            // moderately, matching "slightly too far" rather than
            // assuming it needs a drastic cut. Single constant, easy to
            // retune further either direction.
            return distance * 5.0f;
        }
        return distance;
    }
}