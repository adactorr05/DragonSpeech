package com.dragonspeech.client.mixin;

import com.dragonspeech.client.accessors.ModelPartAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds per-axis scale to vanilla's ModelPart by injecting an extra
 * PoseStack.scale() call at the tail of translateAndRotate - see
 * ModelPartAccess (same package tree, client/accessors) for why this
 * exists. Ported from Dragon Mounts Legacy
 * (com.github.kay9.dragonmounts.mixins.client.ModelPartMixin, GPL-3.0)
 * - same mixin syntax works identically on Fabric, only the
 * registration JSON differs (this project's own
 * dragonspeech.client.mixins.json instead of DML's Forge-specific
 * mixin config).
 */
@Mixin(ModelPart.class)
public class ModelPartMixin implements ModelPartAccess {

    @Unique
    public float dragonspeech$xScale = 1;
    @Unique
    public float dragonspeech$yScale = 1;
    @Unique
    public float dragonspeech$zScale = 1;

    @Inject(method = "translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "TAIL"))
    public void dragonspeech$scalePoseStack(PoseStack poseStack, CallbackInfo ci) {
        poseStack.scale(dragonspeech$xScale, dragonspeech$yScale, dragonspeech$zScale);
    }

    @Override
    public float getXScale() {
        return dragonspeech$xScale;
    }

    @Override
    public float getYScale() {
        return dragonspeech$yScale;
    }

    @Override
    public float getZScale() {
        return dragonspeech$zScale;
    }

    @Override
    public void setXScale(float x) {
        this.dragonspeech$xScale = x;
    }

    @Override
    public void setYScale(float y) {
        this.dragonspeech$yScale = y;
    }

    @Override
    public void setZScale(float z) {
        this.dragonspeech$zScale = z;
    }
}
