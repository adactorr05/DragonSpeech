package com.dragonspeech.client.dragon;

import com.dragonspeech.client.accessors.ModelPartAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;

import java.util.List;

/**
 * A proxy that snapshots one ModelPart's transform so it can be
 * projected onto other, structurally-identical parts (this is how the
 * neck/tail's repeated segments all render from the same underlying
 * geometry with per-segment position/rotation, without needing N
 * separate model definitions for N segments). Ported near-verbatim
 * from Dragon Mounts Legacy
 * (com.github.kay9.dragonmounts.client.ModelPartProxy, GPL-3.0,
 * original author credit: Nico Bergemann).
 */
@SuppressWarnings("DataFlowIssue")
public class ModelPartProxy {

    public final ModelPart part;
    private final List<ModelPartProxy> children;
    public float scaleX = 1;
    public float scaleY = 1;
    public float scaleZ = 1;
    private float x;
    private float y;
    private float z;
    private float xRot;
    private float yRot;
    private float zRot;
    private boolean visible;

    public ModelPartProxy(ModelPart part) {
        this.part = part;
        children = part.getAllParts().skip(1).map(ModelPartProxy::new).toList();
        update();
    }

    public void copy(ModelPartProxy other) {
        other.x = x;
        other.y = y;
        other.z = z;

        other.xRot = xRot;
        other.yRot = yRot;
        other.zRot = zRot;

        other.scaleX = scaleX;
        other.scaleY = scaleY;
        other.scaleZ = scaleZ;

        other.visible = visible;

        if (children.size() != other.children.size()) {
            throw new IllegalArgumentException("Proxies do not share the same children.");
        }
        for (int i = 0; i < children.size(); i++) {
            children.get(i).copy(other.children.get(i));
        }
    }

    /** Saves the model part's current transform onto this proxy. */
    public final void update() {
        x = part.x;
        y = part.y;
        z = part.z;

        xRot = part.xRot;
        yRot = part.yRot;
        zRot = part.zRot;

        ModelPartAccess mixinPart = (ModelPartAccess) (Object) part;

        scaleX = mixinPart.getXScale();
        scaleY = mixinPart.getYScale();
        scaleZ = mixinPart.getZScale();

        visible = part.visible;

        for (ModelPartProxy child : children) {
            child.update();
        }
    }

    /** Restores this proxy's transform onto the model part. */
    public final void apply() {
        part.x = x;
        part.y = y;
        part.z = z;

        part.xRot = xRot;
        part.yRot = yRot;
        part.zRot = zRot;

        ((ModelPartAccess) (Object) part).setRenderScale(scaleX, scaleY, scaleZ);

        part.visible = visible;

        for (ModelPartProxy child : children) {
            child.apply();
        }
    }

    public void render(PoseStack poseStack, VertexConsumer vertices, int packedLight, int packedOverlay, int color) {
        apply();
        part.render(poseStack, vertices, packedLight, packedOverlay, color);
    }
}
