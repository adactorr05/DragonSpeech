package com.dragonspeech.client.dragon.vfx;

import com.dragonspeech.DragonSpeech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Adapted from Saints Dragons' own DragonWingTrailRenderer - same
 * ribbon-mesh construction (camera-facing quad strip built from a
 * recorded position history), but rewritten against Minecraft 1.21's
 * real VertexConsumer API rather than their 1.20.1-era one, which
 * changed significantly (vertex()->addVertex(), color()->setColor(),
 * endVertex() removed entirely, etc - confirmed this via search before
 * writing any of it, not assumed, since guessing wrong here would have
 * been a real, silent compile failure).
 */
public final class DragonWingTrailRenderer {
    private static final ResourceLocation TEXTURE = DragonSpeech.id("textures/particle/trail.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);
    private static final float WIDTH = 0.25F;

    private DragonWingTrailRenderer() {
    }

    public static void render(DragonWingTrail trail, MultiBufferSource bufferSource, PoseStack.Pose pose) {
        int count = trail.getPointCount();
        if (count < 2) {
            return;
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
        int light = LightTexture.FULL_BRIGHT;
        int segments = count - 1;

        Vector3f[] points = new Vector3f[count];
        Vector3f[] rightVectors = new Vector3f[count];
        for (int i = 0; i < count; i++) {
            Vec3 world = trail.getPositionAt(i);
            if (world == null) {
                return;
            }
            points[i] = new Vector3f(
                    (float) (world.x - cameraPos.x),
                    (float) (world.y - cameraPos.y),
                    (float) (world.z - cameraPos.z)
            );
        }

        Vector3f fallbackRight = new Vector3f(WIDTH, 0.0F, 0.0F);
        for (int i = 0; i < count; i++) {
            Vector3f direction = new Vector3f();
            if (i > 0 && i < count - 1) {
                addNormalized(direction, points[i - 1], points[i]);
                addNormalized(direction, points[i], points[i + 1]);
            } else if (i == 0) {
                addNormalized(direction, points[0], points[1]);
            } else {
                addNormalized(direction, points[i - 1], points[i]);
            }

            if (direction.lengthSquared() < 1.0E-5F) {
                rightVectors[i] = new Vector3f(fallbackRight);
                continue;
            }
            direction.normalize();

            Vector3f cameraToPoint = new Vector3f(points[i]);
            if (cameraToPoint.lengthSquared() < 1.0E-5F) {
                cameraToPoint.set(0.0F, 0.0F, 1.0F);
            } else {
                cameraToPoint.normalize();
            }
            Vector3f right = new Vector3f(direction).cross(cameraToPoint);
            if (right.lengthSquared() < 1.0E-5F) {
                rightVectors[i] = new Vector3f(fallbackRight);
            } else {
                right.normalize().mul(WIDTH);
                rightVectors[i] = right;
                fallbackRight.set(right);
            }
        }

        for (int i = 0; i < segments; i++) {
            float u1 = i / (float) segments;
            float u2 = (i + 1) / (float) segments;
            float trailFade1 = i / (float) count;
            float trailFade2 = (i + 1) / (float) count;
            float a1 = Mth.clamp(trail.getAlphaAt(i) * trailFade1, 0.0F, 1.0F);
            float a2 = Mth.clamp(trail.getAlphaAt(i + 1) * trailFade2, 0.0F, 1.0F);

            Vector3f p1 = points[i];
            Vector3f p2 = points[i + 1];
            Vector3f r1 = rightVectors[i];
            Vector3f r2 = rightVectors[i + 1];

            vertex(consumer, pose, new Vector3f(p1).add(r1), u1, 0.0F, a1, light);
            vertex(consumer, pose, new Vector3f(p1).sub(r1), u1, 1.0F, a1, light);
            vertex(consumer, pose, new Vector3f(p2).sub(r2), u2, 1.0F, a2, light);
            vertex(consumer, pose, new Vector3f(p2).add(r2), u2, 0.0F, a2, light);

            vertex(consumer, pose, new Vector3f(p2).add(r2), u2, 0.0F, a2, light);
            vertex(consumer, pose, new Vector3f(p2).sub(r2), u2, 1.0F, a2, light);
            vertex(consumer, pose, new Vector3f(p1).sub(r1), u1, 1.0F, a1, light);
            vertex(consumer, pose, new Vector3f(p1).add(r1), u1, 0.0F, a1, light);
        }
    }

    // FIX: real 1.21 API, confirmed via search before writing this -
    // vertex()->addVertex() (returns VertexConsumer for chaining now,
    // not void), color()->setColor(), uv()->setUv(),
    // overlayCoords()->setOverlay() (setUv1(int,int) also exists but
    // takes raw unpacked u/v coordinates separately, not a packed
    // value like OverlayTexture.NO_OVERLAY already is - real crash log
    // error, picked the wrong one of the two replacement options my
    // own search turned up), uv2()->setUv2(), normal()->setNormal()
    // (a PoseStack.Pose-taking overload exists that transforms by the
    // pose's own normal matrix automatically, matching what the old
    // Matrix3f-transform argument used to do), endVertex() removed
    // entirely (called automatically now).
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f pos,
                               float u, float v, float alpha, int light) {
        consumer.addVertex(pos.x(), pos.y(), pos.z())
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                // FIX (caught proactively, same mistake pattern as the
                // overlay one the real crash flagged): setUv2(int,int)
                // takes two separate unpacked values; light is already
                // a packed value (LightTexture.FULL_BRIGHT), which
                // needs setLight(int) instead.
                .setLight(light)
                .setNormal(pose, 1.0F, 0.0F, 0.0F);
    }

    private static void addNormalized(Vector3f target, Vector3f from, Vector3f to) {
        Vector3f direction = new Vector3f(to).sub(from);
        if (direction.lengthSquared() >= 1.0E-5F) {
            target.add(direction.normalize());
        }
    }
}