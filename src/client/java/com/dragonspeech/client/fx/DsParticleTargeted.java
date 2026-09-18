package com.dragonspeech.client.fx;

import com.dragonspeech.DragonSpeech;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Base class for "stretched" particles - beams and lightning arcs that
 * connect an origin to a target point/entity. Port of EBW's
 * ParticleTargeted.
 *
 * RENDERING NOTE (important, 1.21.1-specific): the original declared
 * ParticleRenderType.CUSTOM and did all drawing itself. In 1.21.1 the
 * particle engine skips types whose begin() returns a null buffer, so
 * CUSTOM particles would never render. Instead, these particles stay on
 * the standard translucent sheet (so render() is reliably called), write
 * NOTHING into the sheet's buffer, and draw their own geometry
 * immediately with their own Tesselator buffer. restoreSheetState() then
 * puts the shader/texture/blend state back exactly the way
 * PARTICLE_SHEET_TRANSLUCENT.begin() left it, so the ordinary particles
 * drawn after us still render correctly.
 */
public abstract class DsParticleTargeted extends DsParticle {

    private static final double THIRD_PERSON_AXIAL_OFFSET = 1.2;

    protected double targetX = Double.NaN;
    protected double targetY = Double.NaN;
    protected double targetZ = Double.NaN;
    protected double targetVelX = Double.NaN;
    protected double targetVelY = Double.NaN;
    protected double targetVelZ = Double.NaN;

    protected double length;

    protected Entity target = null;

    public DsParticleTargeted(ClientLevel world, double x, double y, double z, SpriteSet spriteProvider, boolean updateTextureOnTick) {
        super(world, x, y, z, spriteProvider, updateTextureOnTick);
    }

    /** The particle stretches to touch this position (unless a target entity or a length overrides it). */
    public void setTargetPosition(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    /** Moves the target point at this velocity each tick. */
    public void setTargetVelocity(double vx, double vy, double vz) {
        this.targetVelX = vx;
        this.targetVelY = vy;
        this.targetVelZ = vz;
    }

    /** The particle stretches to touch this entity. */
    public void setTargetEntity(Entity target) {
        this.target = target;
    }

    /** With a linked origin entity, stretches this far along that entity's line of sight instead. */
    public void setLength(double length) {
        this.length = length;
    }

    @Override
    public void tick() {
        super.tick();

        if (!Double.isNaN(targetVelX) && !Double.isNaN(targetVelY) && !Double.isNaN(targetVelZ)) {
            this.targetX += this.targetVelX;
            this.targetY += this.targetVelY;
            this.targetZ += this.targetVelZ;
        }
    }

    private Vec3 getPosition(Entity entity, double yOffset, float partialTick) {
        double d0 = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double d1 = Mth.lerp(partialTick, entity.yOld, entity.getY()) + yOffset;
        double d2 = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        return new Vec3(d0, d1, d2);
    }

    @Override
    protected void updateEntityLinking(float partialTicks) {
        // Entity linking is handled directly in render() below.
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Entity viewer = camera.getEntity();
        PoseStack stack = new PoseStack();
        double originX, originY, originZ;

        if (this.entity != null) {
            Vec3 entityPos = this.getPosition(entity, 0, tickDelta);
            originX = entityPos.x + relativeX;
            originY = entityPos.y + relativeY;
            originZ = entityPos.z + relativeZ;
        } else {
            originX = Mth.lerp(tickDelta, this.xo, this.x);
            originY = Mth.lerp(tickDelta, this.yo, this.y);
            originZ = Mth.lerp(tickDelta, this.zo, this.z);
        }

        if (this.entity != null && this.shouldApplyOriginOffset()) {
            boolean isFirstPerson = (this.entity == viewer && Minecraft.getInstance().options.getCameraType() != CameraType.THIRD_PERSON_FRONT);

            if (!isFirstPerson || this.shouldApplyOriginOffsetInFirstPerson()) {
                Vec3 look = entity.getViewVector(tickDelta).scale(THIRD_PERSON_AXIAL_OFFSET);
                originX += look.x;
                originY += look.y;
                originZ += look.z;
            }
        }

        double finalTargetX, finalTargetY, finalTargetZ;

        if (this.target != null) {
            Vec3 targetPos = this.getPosition(target, (double) target.getBbHeight() * 0.5D, tickDelta);
            finalTargetX = targetPos.x;
            finalTargetY = targetPos.y;
            finalTargetZ = targetPos.z;
        } else if (this.entity != null && this.length > 0) {
            Vec3 look = entity.getViewVector(tickDelta).scale(length);
            finalTargetX = originX + look.x;
            finalTargetY = originY + look.y;
            finalTargetZ = originZ + look.z;
        } else {
            finalTargetX = this.targetX;
            finalTargetY = this.targetY;
            finalTargetZ = this.targetZ;

            if (!Double.isNaN(targetVelX) && !Double.isNaN(targetVelY) && !Double.isNaN(targetVelZ)) {
                finalTargetX += tickDelta * this.targetVelX;
                finalTargetY += tickDelta * this.targetVelY;
                finalTargetZ += tickDelta * this.targetVelZ;
            }
        }

        if (Double.isNaN(finalTargetX) || Double.isNaN(finalTargetY) || Double.isNaN(finalTargetZ)) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Targeted particle rendered with no target entity, position, or length.");
            return;
        }

        stack.pushPose();
        stack.translate(originX - camera.getPosition().x, originY - camera.getPosition().y, originZ - camera.getPosition().z);

        double dx = finalTargetX - originX;
        double dy = finalTargetY - originY;
        double dz = finalTargetZ - originZ;

        float beamLength = Mth.sqrt((float) (dx * dx + dy * dy + dz * dz));
        Vec3 direction = new Vec3(dx, dy, dz).normalize();

        float yaw = (float) (Math.atan2(direction.x, direction.z) * 180.0D / Math.PI);
        float pitch = (float) (Math.asin(-direction.y) * 180.0D / Math.PI);

        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));

        Tesselator tesselator = Tesselator.getInstance();
        this.draw(stack, tesselator, beamLength, tickDelta);

        stack.popPose();

        restoreSheetState();
    }

    protected boolean shouldApplyOriginOffset() {
        return true;
    }

    protected boolean shouldApplyOriginOffsetInFirstPerson() {
        return false;
    }

    protected abstract void draw(PoseStack stack, Tesselator tesselator, float length, float tickDelta);
}
