package com.dragonspeech.client.dragon;

import com.dragonspeech.client.accessors.ModelPartAccess;
import com.dragonspeech.dragon.DragonEntity;
import com.dragonspeech.util.CircularBuffer;
import com.dragonspeech.util.LerpedFloat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Animation control class to put useless reptiles in motion. Ported
 * near-verbatim from Dragon Mounts Legacy
 * (com.github.kay9.dragonmounts.client.DragonAnimator, GPL-3.0,
 * https://github.com/TheRealKingslayer1/Dragon-Mounts-Legacy, original
 * author credit: Nico Bergemann) - every tuned constant, curve, and
 * coefficient in here (the wing flutter/glide arrays, the leg
 * stand/sit/walk keyframes, the Catmull-Rom spline weights) is the
 * actual, hand-fitted animation feel - none of it improvised or
 * "cleaned up", since a changed number here is a genuinely different
 * animation, not a style preference. Only TameableDragon -> DragonEntity
 * and package names adapted.
 *
 * DragonEntity (not yet ported - next piece) needs to provide every
 * method/field this file calls on `dragon`: onWingsDown(float),
 * isSaddled(), isFlying(), getHealth(), getHealthFraction(), getX/Y/Z(),
 * isInSittingPose(), isNearGround(), getAgeScale() - plus the vanilla
 * Entity fields xo/yo/zo, horizontalCollision, and yBodyRot, which any
 * Entity subclass already has.
 */
@SuppressWarnings({"unused", "DataFlowIssue"})
public class DragonAnimator implements com.dragonspeech.dragon.ClientTickableAnimator {
    private static final int JAW_OPENING_TIME_FOR_ATTACK = 5;

    private final DragonEntity dragon;

    private float partialTicks;
    private float moveTime;
    private float moveSpeed;
    private float lookYaw;
    private float lookPitch;
    private double prevRenderYawOffset;
    private double yawAbs;

    // "I do want them leaning while turning like saints" per explicit
    // direction - ported directly from Saints Dragons' own real
    // DragonFlightVisuals.tickBanking(), a genuinely separate mechanic
    // from their barrel-roll system (different state, different file) -
    // confirmed this by reading their actual source rather than assume
    // "leaning" and "rolling" were the same feature there. Field names
    // and tuning constants kept matching their own.
    private float bankSmoothedYaw;
    private float bankAngle;
    private float prevBankAngle;

    private float animBase;
    private float cycleOfs;
    private float anim;
    private float ground;
    private float flutter;
    private float walk;
    private float sit;
    private float jaw;
    private float speed;

    private final LerpedFloat animTimer = new LerpedFloat();
    private final LerpedFloat groundTimer = new LerpedFloat.Clamped(1, 0, 1);
    private final LerpedFloat flutterTimer = LerpedFloat.unit();
    private final LerpedFloat walkTimer = LerpedFloat.unit();
    private final LerpedFloat sitTimer = LerpedFloat.unit();
    private final LerpedFloat jawTimer = LerpedFloat.unit();
    private final LerpedFloat speedTimer = new LerpedFloat.Clamped(1, 0, 1);

    private boolean initTrails = false;
    private final CircularBuffer yTrail = new CircularBuffer(8);
    private final CircularBuffer yawTrail = new CircularBuffer(16);
    private final CircularBuffer pitchTrail = new CircularBuffer(16);

    private boolean onGround;
    private boolean openJaw;
    private boolean wingsDown;

    private final float[] wingArm = new float[3];
    private final float[] wingForearm = new float[3];
    private final float[] wingArmFlutter = new float[3];
    private final float[] wingForearmFlutter = new float[3];
    private final float[] wingArmGlide = new float[3];
    private final float[] wingForearmGlide = new float[3];
    private final float[] wingArmGround = new float[3];
    private final float[] wingForearmGround = new float[3];

    private final float[] xGround = {0, 0, 0, 0};

    // 1st dim - front, hind ; 2nd dim - thigh, crus, foot, toe
    private final float[][] xGroundStand = {
            {0.8f, -1.5f, 1.3f, 0},
            {-0.3f, 1.5f, -0.2f, 0},
    };
    private final float[][] xGroundSit = {
            {0.3f, -1.8f, 1.8f, 0},
            {-0.8f, 1.8f, -0.9f, 0},
    };

    // 1st dim - keyframe ; 2nd dim - front, hind ; 3rd dim - thigh, crus, foot, toe
    private final float[][][] xGroundWalk = {{
            {0.4f, -1.4f, 1.3f, 0},
            {0.1f, 1.2f, -0.5f, 0}
    }, {
            {1.2f, -1.6f, 1.3f, 0},
            {-0.3f, 2.1f, -0.9f, 0.6f}
    }, {
            {0.9f, -2.1f, 1.8f, 0.6f},
            {-0.7f, 1.4f, -0.2f, 0}
    }};

    private final float[] xGroundWalk2 = {0, 0, 0, 0};

    private final float[] yGroundStand = {-0.25f, 0.25f};
    private final float[] yGroundSit = {0.1f, 0.35f};
    private final float[] yGroundWalk = {-0.1f, 0.1f};

    private final float[][] xAirAll = {{0, 0, 0, 0}, {0, 0, 0, 0}};
    private final float[] yAirAll = {-0.1f, 0.1f};

    public DragonAnimator(DragonEntity dragon) {
        this.dragon = dragon;
    }

    public void setPartialTicks(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public void setMovement(float moveTime, float moveSpeed) {
        this.moveTime = moveTime;
        this.moveSpeed = moveSpeed;
    }

    public void setLook(float lookYaw, float lookPitch) {
        this.lookYaw = Mth.clamp(lookYaw, -120, 120);
        this.lookPitch = Mth.clamp(lookPitch, -90, 90);
    }

    /** Applies the animations onto the model. Called every frame before the model renders. */
    public void animate(DragonModel model) {
        anim = animTimer.get(partialTicks);
        ground = groundTimer.get(partialTicks);
        flutter = flutterTimer.get(partialTicks);
        walk = walkTimer.get(partialTicks);
        sit = sitTimer.get(partialTicks);
        jaw = jawTimer.get(partialTicks);
        speed = speedTimer.get(partialTicks);

        animBase = anim * ((float) Math.PI) * 2;
        cycleOfs = Mth.sin(animBase - 1) + 1;

        boolean newWingsDown = cycleOfs > 1;
        if (newWingsDown && !wingsDown && flutter != 0) {
            dragon.onWingsDown(speed);
        }
        wingsDown = newWingsDown;

        model.back.visible = !dragon.isSaddled();

        cycleOfs = (cycleOfs * cycleOfs + cycleOfs * 2) * 0.05f;

        cycleOfs *= Mth.clampedLerp(0.5f, 1, flutter);
        cycleOfs *= Mth.clampedLerp(1, 0.5f, ground);

        animHeadAndNeck(model);
        animTail(model);
        animWings(model);
        animLegs(model);
    }

    public void tick() {
        setOnGround(!dragon.isFlying());

        if (!initTrails) {
            yTrail.fill((float) dragon.getY());
            yawTrail.fill(dragon.yBodyRot);
            pitchTrail.fill(getModelPitch());
            initTrails = true;
        }

        if (dragon.getHealth() <= 0) {
            animTimer.sync();
            groundTimer.sync();
            flutterTimer.sync();
            walkTimer.sync();
            sitTimer.sync();
            jawTimer.sync();
            return;
        }

        float speedMax = 0.05f;
        float xD = (float) dragon.getX() - (float) dragon.xo;
        float yD = (float) dragon.getY() - (float) dragon.yo;
        float zD = (float) dragon.getZ() - (float) dragon.zo;
        float speedEnt = (xD * xD + zD * zD);
        float speedMulti = Mth.clamp(speedEnt / speedMax, 0, 1);

        float animAdd = 0.035f;

        if (!onGround) {
            animAdd += (1 - speedMulti) * animAdd;
        }

        animTimer.add(animAdd);

        float groundVal = groundTimer.get();
        if (onGround) {
            groundVal *= 0.95f;
            groundVal += 0.08f;
        } else {
            groundVal -= 0.1f;
        }
        groundTimer.set(groundVal);

        boolean flutterFlag = !onGround && (dragon.horizontalCollision || yD > -0.1 || speedEnt < speedMax);
        flutterTimer.add(flutterFlag ? 0.1f : -0.1f);

        boolean walkFlag = moveSpeed > 0.1 && !dragon.isInSittingPose();
        float walkVal = 0.1f;
        walkTimer.add(walkFlag ? walkVal : -walkVal);

        float sitVal = sitTimer.get();
        sitVal += dragon.isInSittingPose() ? 0.1f : -0.1f;
        sitVal *= 0.95f;
        sitTimer.set(sitVal);

        boolean speedFlag = speedEnt > speedMax || dragon.isNearGround();
        float speedValue = 0.05f;
        speedTimer.add(speedFlag ? speedValue : -speedValue);

        double yawDiff = dragon.yBodyRot - prevRenderYawOffset;
        prevRenderYawOffset = dragon.yBodyRot;

        if (yawDiff < 180 && yawDiff > -180) {
            yawAbs += yawDiff;
        }

        // "I do want them leaning while turning like saints" - ported
        // directly from DragonFlightVisuals.tickBanking(), same
        // constants (BANK_YAW_MEMORY=0.70/BANK_YAW_BLEND=0.30 for
        // smoothing the raw per-tick turn rate, BANK_SCALE=5.5 to turn
        // that into a bank angle, BANK_LERP=0.32 for how quickly the
        // visual angle catches up to the target) - reuses yawDiff
        // (already computed above from dragon.yBodyRot) as the same
        // turn-rate signal Saints computes from yRot-yRotO directly.
        prevBankAngle = bankAngle;
        if (!dragon.isFlying()) {
            bankSmoothedYaw = 0f;
            bankAngle = 0f;
            prevBankAngle = 0f;
        } else if (dragon.horizontalCollision) {
            bankSmoothedYaw *= 0.45f;
            bankAngle = Mth.lerp(0.55f, bankAngle, 0f);
            if (Math.abs(bankAngle) < 0.01f) {
                bankAngle = 0f;
            }
        } else {
            bankSmoothedYaw = bankSmoothedYaw * 0.70f + (float) yawDiff * 0.30f;
            float targetBankAngle = Mth.clamp(bankSmoothedYaw * 5.5f, -90f, 90f);
            bankAngle = Mth.lerp(0.32f, bankAngle, targetBankAngle);
            if (Math.abs(bankAngle) < 0.01f) {
                bankAngle = 0f;
            }
        }

        yTrail.update((float) dragon.getY());
        yawTrail.update((float) -yawAbs);
        pitchTrail.update(getModelPitch());
    }

    /** For DragonRenderer to apply as a whole-model roll rotation, interpolated the same simple way getModelPitch() already blends between ticks. */
    public float getBankAngle(float pt) {
        return Mth.lerp(pt, prevBankAngle, bankAngle);
    }

    protected void animHeadAndNeck(DragonModel model) {
        model.neck.setPos(0, 14, -8);
        model.neck.setRotation(0, 0, 0);

        float health = dragon.getHealthFraction();
        float neckSize;

        for (int i = 0; i < model.neckProxy.length; i++) {
            float vertMulti = (i + 1) / (float) model.neckProxy.length;

            float baseRotX = Mth.cos((float) i * 0.45f + animBase) * 0.15f;
            baseRotX *= Mth.clampedLerp(0.2f, 1, flutter);
            baseRotX *= Mth.clampedLerp(1, 0.2f, sit);
            float ofsRotX = Mth.sin(vertMulti * ((float) Math.PI) * 0.9f) * 0.75f;

            model.neck.xRot = baseRotX;
            model.neck.xRot *= terpSmoothStep(1, 0.5f, walk);
            model.neck.xRot += (1 - speed) * vertMulti;
            model.neck.xRot -= Mth.clampedLerp(0, ofsRotX, ground * health);
            model.neck.yRot = (float) Math.toRadians(lookYaw) * vertMulti * speed;

            float v = Mth.clampedLerp(1.6f, 1, vertMulti);
            ((ModelPartAccess) (Object) model.neck).setRenderScale(v, v, 0.6f);

            model.neckScale.visible = i % 2 != 0 || i == 0;

            model.neckProxy[i].update();

            neckSize = DragonModel.NECK_SIZE * ((ModelPartAccess) (Object) model.neck).getZScale() - 1.4f;
            model.neck.x -= Mth.sin(model.neck.yRot) * Mth.cos(model.neck.xRot) * neckSize;
            model.neck.y += Mth.sin(model.neck.xRot) * neckSize;
            model.neck.z -= Mth.cos(model.neck.yRot) * Mth.cos(model.neck.xRot) * neckSize;
        }

        model.head.xRot = (float) Math.toRadians(lookPitch) + (1 - speed);
        model.head.yRot = model.neck.yRot;
        model.head.zRot = model.neck.zRot * 0.2f;

        model.head.x = model.neck.x;
        model.head.y = model.neck.y;
        model.head.z = model.neck.z;

        model.jaw.xRot = jaw * 0.75f;
        model.jaw.xRot += (1 - Mth.sin(animBase)) * 0.1f * flutter;
    }

    protected void animWings(DragonModel model) {
        float aSpeed = sit > 0 ? 0.6f : 1;

        float a1 = animBase * aSpeed * 0.35f;
        float a2 = animBase * aSpeed * 0.5f;
        float a3 = animBase * aSpeed * 0.75f;

        if (ground < 1) {
            wingArmFlutter[0] = 0.125f - Mth.cos(animBase) * 0.2f;
            wingArmFlutter[1] = 0.25f;
            wingArmFlutter[2] = (Mth.sin(animBase) + 0.125f) * 0.8f;

            wingForearmFlutter[0] = 0;
            wingForearmFlutter[1] = -wingArmFlutter[1] * 2;
            wingForearmFlutter[2] = -(Mth.sin(animBase + 2) + 0.5f) * 0.75f;

            wingArmGlide[0] = -0.25f - Mth.cos(animBase * 2) * Mth.cos(animBase * 1.5f) * 0.04f;
            wingArmGlide[1] = 0.25f;
            wingArmGlide[2] = 0.35f + Mth.sin(animBase) * 0.05f;

            wingForearmGlide[0] = 0;
            wingForearmGlide[1] = -wingArmGlide[1] * 2;
            wingForearmGlide[2] = -0.25f + (Mth.sin(animBase + 2) + 0.5f) * 0.05f;
        }

        if (ground > 0) {
            wingArmGround[0] = 0;
            wingArmGround[1] = 1.4f - Mth.sin(a1) * Mth.sin(a2) * 0.02f;
            wingArmGround[2] = 0.8f + Mth.sin(a2) * Mth.sin(a3) * 0.05f;

            wingArmGround[1] += Mth.sin(moveTime * 0.5f) * 0.02f * walk;
            wingArmGround[2] += Mth.cos(moveTime * 0.5f) * 0.05f * walk;

            wingForearmGround[0] = 0;
            wingForearmGround[1] = -wingArmGround[1] * 2;
            wingForearmGround[2] = 0;
        }

        slerpArrays(wingArmGlide, wingArmFlutter, wingArm, flutter);
        slerpArrays(wingForearmGlide, wingForearmFlutter, wingForearm, flutter);

        slerpArrays(wingArm, wingArmGround, wingArm, ground);
        slerpArrays(wingForearm, wingForearmGround, wingForearm, ground);

        mirrorRotate(model.wingArms[0], model.wingArms[1], wingArm[0], wingArm[1], wingArm[2]);

        mirrorRotate(model.wingForearms[0], model.wingForearms[1], wingForearm[0], wingForearm[1], wingForearm[2]);

        float[] yFold = new float[]{2.7f, 2.8f, 2.9f, 3.0f};
        float[] yUnfold = new float[]{0.1f, 0.9f, 1.7f, 2.5f};

        float rotX = 0;
        float rotYOfs = Mth.sin(a1) * Mth.sin(a2) * 0.03f;
        float rotYMulti = 1;

        for (int i = 0; i < model.wingFingers[0].length; i++) {
            mirrorRotate(model.wingFingers[0][i],
                    model.wingFingers[1][i],
                    rotX += 0.005f,
                    terpSmoothStep(yUnfold[i], yFold[i] + rotYOfs * rotYMulti, ground),
                    0);

            rotYMulti -= 0.2f;
        }
    }

    @SuppressWarnings("UnusedAssignment")
    protected void animTail(DragonModel model) {
        model.tail.x = 0;
        model.tail.y = 16;
        model.tail.z = 62;

        model.tail.xRot = 0;
        model.tail.yRot = 0;
        model.tail.zRot = 0;

        float rotXStand = 0;
        float rotYStand = 0;
        float rotXSit = 0;
        float rotYSit = 0;
        float rotXAir = 0;
        float rotYAir = 0;

        for (int i = 0; i < model.tailProxy.length; i++) {
            float vertMulti = (i + 1) / (float) model.tailProxy.length;

            float amp = 0.1f + i / (model.tailProxy.length * 2f);

            rotXStand = (i - model.tailProxy.length * 0.6f) * -amp * 0.4f;
            rotXStand += (Mth.sin(animBase * 0.2f) * Mth.sin(animBase * 0.37f) * 0.4f * amp - 0.1f) * (1 - sit);
            rotXSit = rotXStand * 0.8f;

            rotYStand = (rotYStand + Mth.sin(i * 0.45f + animBase * 0.5f)) * amp * 0.4f;
            rotYSit = Mth.sin(vertMulti * ((float) Math.PI)) * ((float) Math.PI) * 1.2f - 0.5f;

            rotXAir -= Mth.sin(i * 0.45f + animBase) * 0.04f * Mth.clampedLerp(0.3f, 1, flutter);

            model.tail.xRot = Mth.clampedLerp(rotXStand, rotXSit, sit);
            model.tail.yRot = Mth.clampedLerp(rotYStand, rotYSit, sit);

            model.tail.xRot = Mth.clampedLerp(rotXAir, model.tail.xRot, ground);
            model.tail.yRot = Mth.clampedLerp(rotYAir, model.tail.yRot, ground);

            float angleLimit = 160 * vertMulti;
            float yawOfs = Mth.clamp(yawTrail.get(partialTicks, 0, i + 1) * 2, -angleLimit, angleLimit);
            float pitchOfs = Mth.clamp(pitchTrail.get(partialTicks, 0, i + 1) * 2, -angleLimit, angleLimit);

            model.tail.xRot += Math.toRadians(pitchOfs);
            model.tail.xRot -= (1 - speed) * vertMulti * 2;
            model.tail.yRot += Math.toRadians(180 - yawOfs);

            if (model.tailHornRight != null) {
                var atIndex = i > model.tailProxy.length - 7 && i < model.tailProxy.length - 3;
                model.tailHornLeft.visible = model.tailHornRight.visible = atIndex;
            }

            float neckScale = Mth.clampedLerp(1.5f, 0.3f, vertMulti);
            ((ModelPartAccess) (Object) model.tail).setRenderScale(neckScale, neckScale, neckScale);

            model.tailProxy[i].update();

            float tailSize = DragonModel.TAIL_SIZE * ((ModelPartAccess) (Object) model.tail).getZScale() - 0.7f;
            model.tail.y += Mth.sin(model.tail.xRot) * tailSize;
            model.tail.z -= Mth.cos(model.tail.yRot) * Mth.cos(model.tail.xRot) * tailSize;
            model.tail.x -= Mth.sin(model.tail.yRot) * Mth.cos(model.tail.xRot) * tailSize;
        }
    }

    protected void animLegs(DragonModel model) {
        if (ground < 1) {
            float footAirOfs = cycleOfs * 0.1f;
            float footAirX = 0.75f + cycleOfs * 0.1f;

            xAirAll[0][0] = 1.3f + footAirOfs;
            xAirAll[0][1] = -(0.7f * speed + 0.1f + footAirOfs);
            xAirAll[0][2] = footAirX;
            xAirAll[0][3] = footAirX * 0.5f;

            xAirAll[1][0] = footAirOfs + 0.6f;
            xAirAll[1][1] = footAirOfs + 0.8f;
            xAirAll[1][2] = footAirX;
            xAirAll[1][3] = footAirX * 0.5f;
        }

        // 0 - front leg, right side  1 - hind leg, right side
        // 2 - front leg, left side   3 - hind leg, left side
        for (int i = 0; i < model.legs.length; i++) {
            var thigh = model.legs[i][0];
            var crus = model.legs[i][1];
            var foot = model.legs[i][2];
            var toe = model.legs[i][3];

            thigh.z = (i % 2 == 0) ? 4 : 46;

            float[] xAir = xAirAll[i % 2];

            slerpArrays(xGroundStand[i % 2], xGroundSit[i % 2], xGround, sit);

            xGround[3] = -(xGround[0] + xGround[1] + xGround[2]);

            if (walk > 0) {
                splineArrays(moveTime * 0.2f, i > 1, xGroundWalk2,
                        xGroundWalk[0][i % 2], xGroundWalk[1][i % 2], xGroundWalk[2][i % 2]);
                xGroundWalk2[3] -= xGroundWalk2[0] + xGroundWalk2[1] + xGroundWalk2[2];

                slerpArrays(xGround, xGroundWalk2, xGround, walk);
            }

            float yAir = yAirAll[i % 2];
            float yGround;

            yGround = terpSmoothStep(yGroundStand[i % 2], yGroundSit[i % 2], sit);

            yGround = terpSmoothStep(yGround, yGroundWalk[i % 2], walk);

            thigh.yRot = terpSmoothStep(yAir, yGround, ground);
            thigh.xRot = terpSmoothStep(xAir[0], xGround[0], ground);
            crus.xRot = terpSmoothStep(xAir[1], xGround[1], ground);
            foot.xRot = terpSmoothStep(xAir[2], xGround[2], ground);
            toe.xRot = terpSmoothStep(xAir[3], xGround[3], ground);

            if (i > 1) {
                thigh.yRot *= -1;
            }
        }
    }

    public float getModelPitch() {
        return getModelPitch(partialTicks);
    }

    public float getModelPitch(float pt) {
        float pitchMovingMax = 90;
        float pitchMoving = Mth.clamp(yTrail.get(pt, 5, 0) * 10, -pitchMovingMax, pitchMovingMax);
        float pitchHover = 60;
        return terpSmoothStep(pitchHover, pitchMoving, speed);
    }

    @SuppressWarnings("SameReturnValue")
    public float getModelOffsetX() {
        return 0;
    }

    public float getModelOffsetY() {
        return 1.5f + (-sit * 0.6f);
    }

    public float getModelOffsetZ() {
        return -1.5f;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public void setOpenJaw(boolean openJaw) {
        this.openJaw = openJaw;
    }

    private static void mirrorRotate(ModelPart rightLimb, ModelPart leftLimb, float xRot, float yRot, float zRot) {
        rightLimb.xRot = xRot;
        rightLimb.yRot = yRot;
        rightLimb.zRot = zRot;
        leftLimb.xRot = xRot;
        leftLimb.yRot = -yRot;
        leftLimb.zRot = -zRot;
    }

    private static void slerpArrays(float[] a, float[] b, float[] c, float x) {
        if (a.length != b.length || b.length != c.length) {
            throw new IllegalArgumentException();
        }

        if (x <= 0) {
            System.arraycopy(a, 0, c, 0, a.length);
            return;
        }
        if (x >= 1) {
            System.arraycopy(b, 0, c, 0, a.length);
            return;
        }

        for (int i = 0; i < c.length; i++) {
            c[i] = terpSmoothStep(a[i], b[i], x);
        }
    }

    private static float terpSmoothStep(float a, float b, float x) {
        if (x <= 0) {
            return a;
        }
        if (x >= 1) {
            return b;
        }
        x = x * x * (3 - 2 * x);
        return a * (1 - x) + b * x;
    }

    private static void splineArrays(float x, boolean shift, float[] result, float[]... nodes) {
        int i1 = (int) x % nodes.length;
        int i2 = (i1 + 1) % nodes.length;
        int i3 = (i1 + 2) % nodes.length;

        float[] a1 = nodes[i1];
        float[] a2 = nodes[i2];
        float[] a3 = nodes[i3];

        float xn = x % nodes.length - i1;

        if (shift) {
            terpCatmullRomSpline(xn, result, a2, a3, a1, a2);
        } else {
            terpCatmullRomSpline(xn, result, a1, a2, a3, a1);
        }
    }

    private static final float[][] CR = {
            {-0.5f, 1.5f, -1.5f, 0.5f},
            {1.0f, -2.5f, 2.0f, -0.5f},
            {-0.5f, 0.0f, 0.5f, 0.0f},
            {0.0f, 1.0f, 0.0f, 0.0f}
    };

    // http://www.java-gaming.org/index.php?topic=24122.0
    private static void terpCatmullRomSpline(float x, float[] result, float[]... knots) {
        int nknots = knots.length;
        int nspans = nknots - 3;
        int knot = 0;
        if (nspans < 1) {
            throw new IllegalArgumentException("Spline has too few knots");
        }
        x = Mth.clamp(x, 0, 0.9999f) * nspans;

        int span = (int) x;
        if (span >= nknots - 3) {
            span = nknots - 3;
        }

        x -= span;
        knot += span;

        int dimension = result.length;
        for (int i = 0; i < dimension; i++) {
            float knot0 = knots[knot][i];
            float knot1 = knots[knot + 1][i];
            float knot2 = knots[knot + 2][i];
            float knot3 = knots[knot + 3][i];

            float c3 = CR[0][0] * knot0 + CR[0][1] * knot1 + CR[0][2] * knot2 + CR[0][3] * knot3;
            float c2 = CR[1][0] * knot0 + CR[1][1] * knot1 + CR[1][2] * knot2 + CR[1][3] * knot3;
            float c1 = CR[2][0] * knot0 + CR[2][1] * knot1 + CR[2][2] * knot2 + CR[2][3] * knot3;
            float c0 = CR[3][0] * knot0 + CR[3][1] * knot1 + CR[3][2] * knot2 + CR[3][3] * knot3;

            result[i] = ((c3 * x + c2) * x + c1) * x + c0;
        }
    }
}
