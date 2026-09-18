package com.dragonspeech.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * The straight glowing beam stretched between two points (the geisla ray
 * form's spine) - the same three-layer core/sheath/halo construction as
 * the lightning arc, without the jitter. Shrinks quadratically over its
 * lifetime if one was set. Port of EBW's ParticleBeam, 1.21.1 buffer API.
 */
public class BeamParticle extends DsParticleTargeted {

    private static final float THICKNESS = 0.1f;

    public BeamParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setColor(1, 1, 1);
        this.setLifetime(0);
        this.quadSize = 1;
    }

    @Override
    protected void draw(PoseStack stack, Tesselator tesselator, float length, float tickDelta) {
        float scale = this.quadSize;

        if (this.lifetime > 0) {
            float ageFraction = (age + tickDelta - 1) / lifetime;
            scale = this.quadSize * (1 - ageFraction * ageFraction);
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(240f / 255f, 240f / 255f, 240f / 255f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        for (int layer = 0; layer < 3; layer++) {
            drawSegment(stack, tesselator, layer, length, THICKNESS * scale);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        // (restoreSheetState() in DsParticleTargeted.render() resets
        // cull/blend/shader/shaderColor after this returns.)
    }

    private void drawSegment(PoseStack stack, Tesselator tesselator, int layer, float length, float thickness) {
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        switch (layer) {
            case 0 -> drawShearedBox(stack, buffer, length, 0.25f * thickness, 1, 1, 1, 1);
            case 1 -> drawShearedBox(stack, buffer, length, 0.6f * thickness, (rCol + 1) / 2, (gCol + 1) / 2, (bCol + 1) / 2, 0.65f);
            case 2 -> drawShearedBox(stack, buffer, length, thickness, rCol, gCol, bCol, 0.3f);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }
    }

    private void drawShearedBox(PoseStack stack, BufferBuilder buffer, float length, float width, float r, float g, float b, float a) {
        var pose = stack.last().pose();
        buffer.addVertex(pose, -width, -width, 0).setColor(r, g, b, a);
        buffer.addVertex(pose, -width, -width, length).setColor(r, g, b, a);
        buffer.addVertex(pose, -width, width, 0).setColor(r, g, b, a);
        buffer.addVertex(pose, -width, width, length).setColor(r, g, b, a);
        buffer.addVertex(pose, width, width, 0).setColor(r, g, b, a);
        buffer.addVertex(pose, width, width, length).setColor(r, g, b, a);
        buffer.addVertex(pose, width, -width, 0).setColor(r, g, b, a);
        buffer.addVertex(pose, width, -width, length).setColor(r, g, b, a);
        buffer.addVertex(pose, -width, -width, 0).setColor(r, g, b, a);
        buffer.addVertex(pose, -width, -width, length).setColor(r, g, b, a);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new BeamParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new BeamParticle(level, x, y, z, spriteSet);
        }
    }
}
