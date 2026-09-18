package com.dragonspeech.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * The rising "buff" swirl - an open-ended textured box sleeve (using the
 * ported buff.png with GL_REPEAT wrapping so the arrows scroll) that
 * floats up an entity and fades. This is the one particle in the whole
 * set that leans on entity linking to follow its target. Port of EBW's
 * ParticleBuff, 1.21.1 buffer API; the original's own comment calls its
 * render logic the worst in the mod, and the port keeps its exact
 * behavior anyway - faithful first, pretty later.
 */
public class BuffParticle extends DsParticle {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("dragonspeech", "textures/particle/buff.png");

    private final boolean mirror;

    public BuffParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.xd = 0;
        this.yd = 0.162;
        this.zd = 0;
        this.mirror = random.nextBoolean();
        this.setLifetime(15);
        this.setGravity(false);
        this.hasPhysics = false;
        this.setColor(1, 1, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age > this.lifetime / 2) {
            this.alpha = 2f - 2f * (float) this.age / (float) this.lifetime;
        }
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float partialTicks) {
        updateEntityLinking(partialTicks);

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        // The scroll effect needs the texture to repeat vertically.
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Vec3 cameraPos = camera.getPosition();
        float x, y, z;

        if (entity != null) {
            Vec3 entityPos = entity.getPosition(partialTicks);
            x = (float) (entityPos.x + relativeX - cameraPos.x);
            y = (float) (entityPos.y + relativeY - cameraPos.y);
            z = (float) (entityPos.z + relativeZ - cameraPos.z);
        } else {
            x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cameraPos.x);
            y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cameraPos.y);
            z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cameraPos.z);
        }

        float ageProgress = (float) this.age / (float) this.lifetime;
        float f = 0.875f - 0.125f * Mth.floor(ageProgress * 8 - 0.000001f);
        float g = f + 0.125f;

        float textureOffset = (this.age + partialTicks) / (float) this.lifetime * -2;

        float scale = 0.6f;
        float yScale = 0.7f * scale;
        float dx = mirror ? -scale : scale;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_TEX_COLOR);

        vertex(buffer, x - dx, y - yScale, z - scale, 0 + textureOffset, g);
        vertex(buffer, x - dx, y + yScale, z - scale, 0 + textureOffset, f);
        vertex(buffer, x + dx, y - yScale, z - scale, 0.25f + textureOffset, g);
        vertex(buffer, x + dx, y + yScale, z - scale, 0.25f + textureOffset, f);
        vertex(buffer, x + dx, y - yScale, z + scale, 0.5f + textureOffset, g);
        vertex(buffer, x + dx, y + yScale, z + scale, 0.5f + textureOffset, f);
        vertex(buffer, x - dx, y - yScale, z + scale, 0.75f + textureOffset, g);
        vertex(buffer, x - dx, y + yScale, z + scale, 0.75f + textureOffset, f);
        vertex(buffer, x - dx, y - yScale, z - scale, 1.0f + textureOffset, g);
        vertex(buffer, x - dx, y + yScale, z - scale, 1.0f + textureOffset, f);

        MeshData mesh = buffer.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }

        restoreSheetState();
    }

    private void vertex(BufferBuilder buffer, float x, float y, float z, float u, float v) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(rCol, gCol, bCol, alpha);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880; // full brightness, always
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new BuffParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new BuffParticle(level, x, y, z, spriteSet);
        }
    }
}
