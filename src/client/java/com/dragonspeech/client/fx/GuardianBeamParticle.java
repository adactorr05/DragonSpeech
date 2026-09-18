package com.dragonspeech.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * A rotating, texture-scrolling beam using vanilla's guardian beam
 * texture - the "sustained magical ray" look. Port of EBW's
 * ParticleGuardianBeam, 1.21.1 buffer API (and the position-tex-color
 * shader the format actually requires).
 */
public class GuardianBeamParticle extends DsParticleTargeted {

    private static final float THICKNESS = 0.15f;

    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png");

    public GuardianBeamParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setColor(1, 1, 1);
        this.setLifetime(3);
        this.quadSize = 1;
    }

    @Override
    protected void draw(PoseStack stack, Tesselator tesselator, float length, float tickDelta) {
        float scale = this.quadSize;

        stack.pushPose();

        // Slow constant rotation around the beam axis.
        float spin = Minecraft.getInstance().player != null
            ? Minecraft.getInstance().player.tickCount + tickDelta
            : age + tickDelta;
        stack.mulPose(Axis.ZP.rotationDegrees(spin));

        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float t = THICKNESS * scale;
        float v1 = 2 * (age + tickDelta) / lifetime; // scrolling texture
        float v2 = v1 + length * 2;

        var pose = stack.last().pose();
        buffer.addVertex(pose, -t, 0, 0).setUv(0, v1).setColor(rCol, gCol, bCol, 1f);
        buffer.addVertex(pose, t, 0, 0).setUv(0.5f, v1).setColor(rCol, gCol, bCol, 1f);
        buffer.addVertex(pose, t, 0, length).setUv(0.5f, v2).setColor(rCol, gCol, bCol, 1f);
        buffer.addVertex(pose, -t, 0, length).setUv(0, v2).setColor(rCol, gCol, bCol, 1f);

        buffer.addVertex(pose, 0, -t, 0).setUv(0, v1).setColor(rCol, gCol, bCol, 1f);
        buffer.addVertex(pose, 0, t, 0).setUv(0.5f, v1).setColor(rCol, gCol, bCol, 1f);
        buffer.addVertex(pose, 0, t, length).setUv(0.5f, v2).setColor(rCol, gCol, bCol, 1f);
        buffer.addVertex(pose, 0, -t, length).setUv(0, v2).setColor(rCol, gCol, bCol, 1f);

        MeshData mesh = buffer.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.disableBlend();
        stack.popPose();
        // (restoreSheetState() rebinds the particle atlas + shader after this.)
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new GuardianBeamParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GuardianBeamParticle(level, x, y, z, spriteSet);
        }
    }
}
