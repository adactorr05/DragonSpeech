package com.dragonspeech.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The expanding translucent sphere shell used by bursts and orb impacts -
 * a lat/long triangle-strip sphere drawn twice (inside and outside faces)
 * that grows from nothing to its full radius while fading out. Port of
 * EBW's ParticleSphere, 1.21.1 buffer API; renders immediately from
 * within the translucent-sheet pass (see DsParticleTargeted's comment)
 * and restores the sheet state afterward.
 */
public class SphereParticle extends DsParticle {

    public SphereParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setColor(1, 1, 1);
        this.lifetime = 5;
        this.alpha = 0.8f;
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        PoseStack stack = new PoseStack();

        updateEntityLinking(tickDelta);

        float x = (float) (this.xo + (this.x - this.xo) * (double) tickDelta);
        float y = (float) (this.yo + (this.y - this.yo) * (double) tickDelta);
        float z = (float) (this.zo + (this.z - this.zo) * (double) tickDelta);

        stack.pushPose();
        stack.translate(x - camera.getPosition().x, y - camera.getPosition().y, z - camera.getPosition().z);

        RenderSystem.enableBlend();
        RenderSystem.enableCull();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        float latStep = (float) Math.PI / 20;
        float longStep = (float) Math.PI / 20;

        float size = this.quadSize * 10.0f;

        float sphereRadius = size * (this.age + tickDelta - 1) / this.lifetime;
        float alphaNow = this.alpha * (1 - (this.age + tickDelta - 1) / this.lifetime);

        Tesselator tesselator = Tesselator.getInstance();
        drawSphere(stack, tesselator, sphereRadius, latStep, longStep, true, rCol, gCol, bCol, alphaNow);
        drawSphere(stack, tesselator, sphereRadius, latStep, longStep, false, rCol, gCol, bCol, alphaNow);

        RenderSystem.disableBlend();

        stack.popPose();

        restoreSheetState();
    }

    private static void drawSphere(PoseStack stack, Tesselator tesselator, float radius, float latStep, float longStep, boolean inside, float r, float g, float b, float a) {
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        var pose = stack.last().pose();

        boolean goingUp = inside;

        buffer.addVertex(pose, 0, goingUp ? -radius : radius, 0).setColor(r, g, b, a);

        for (float longitude = -(float) Math.PI; longitude <= (float) Math.PI; longitude += longStep) {
            for (float theta = (float) Math.PI / 2 - latStep; theta >= -(float) Math.PI / 2 + latStep; theta -= latStep) {
                float latitude = goingUp ? -theta : theta;

                float hRadius = radius * Mth.cos(latitude);
                float vy = radius * Mth.sin(latitude);
                float vx = hRadius * Mth.sin(longitude);
                float vz = hRadius * Mth.cos(longitude);

                buffer.addVertex(pose, vx, vy, vz).setColor(r, g, b, a);

                vx = hRadius * Mth.sin(longitude + longStep);
                vz = hRadius * Mth.cos(longitude + longStep);

                buffer.addVertex(pose, vx, vy, vz).setColor(r, g, b, a);
            }

            buffer.addVertex(pose, 0, goingUp ? radius : -radius, 0).setColor(r, g, b, a);

            goingUp = !goingUp;
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new SphereParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SphereParticle(level, x, y, z, spriteSet);
        }
    }
}
