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
 * The forked lightning arc stretched between two points - three nested
 * translucent layers (white core, half-bright sheath, coloured halo) of
 * randomly-jittered segments, with random forks that can fork again.
 * The shape is seed-stable per tick, so the whole bolt jitters as one.
 *
 * Port of EBW's ParticleLightning, adapted to the 1.21.1 buffer API:
 * Tesselator.begin() returns the BufferBuilder, vertices go through
 * addVertex().setColor(), and drawing is build() + drawWithShader(MeshData).
 * See DsParticleTargeted's class comment for why this renders immediately
 * from within a translucent-sheet render() call rather than declaring
 * ParticleRenderType.CUSTOM.
 */
public class LightningParticle extends DsParticleTargeted {

    private static final float THICKNESS = 0.04f;
    private static final float MAX_SEGMENT_LENGTH = 0.6f;
    private static final float MIN_SEGMENT_LENGTH = 0.2f;
    private static final float VERTEX_JITTER = 0.15f;
    private static final int MAX_FORK_SEGMENTS = 3;
    private static final float FORK_CHANCE = 0.3f;
    private static final int UPDATE_PERIOD = 1;

    public LightningParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        seed = this.random.nextLong();
        this.setColor(0.2f, 0.6f, 1);
        this.setLifetime(3);
        this.quadSize = 2.4f;
    }

    @Override
    protected void draw(PoseStack stack, Tesselator tesselator, float length, float tickDelta) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        boolean freeEnd = this.target == null;
        int numberOfSegments = Math.round(length / MAX_SEGMENT_LENGTH);

        for (int layer = 0; layer < 3; layer++) {
            float px = 0, py = 0, pz = 0;

            // Re-seed per frame so segments/forks match across the three
            // layers and stay still between shape updates.
            random.setSeed(this.seed + this.age / UPDATE_PERIOD);

            for (int i = 0; i < numberOfSegments - 1; i++) {
                float px2 = (random.nextFloat() * 2 - 1) * VERTEX_JITTER * quadSize;
                float py2 = (random.nextFloat() * 2 - 1) * VERTEX_JITTER * quadSize;
                float pz2 = pz + length / numberOfSegments;

                drawSegment(stack, tesselator, layer, px, py, pz, px2, py2, pz2, THICKNESS * quadSize);

                if (random.nextFloat() < FORK_CHANCE) {
                    float px3 = px, py3 = py, pz3 = pz;

                    for (int j = 0; j < random.nextInt(MAX_FORK_SEGMENTS - 1) + 1; j++) {
                        float px4 = px3 + (random.nextFloat() * 2 - 1) * VERTEX_JITTER * quadSize;
                        float py4 = py3 + (random.nextFloat() * 2 - 1) * VERTEX_JITTER * quadSize;
                        float pz4 = pz3 + MIN_SEGMENT_LENGTH + random.nextFloat() * (MAX_SEGMENT_LENGTH - MIN_SEGMENT_LENGTH);

                        drawSegment(stack, tesselator, layer, px3, py3, pz3, px4, py4, pz4, THICKNESS * 0.8f * quadSize);

                        if (random.nextFloat() < FORK_CHANCE) {
                            float px5 = px3 + (random.nextFloat() * 2 - 1) * VERTEX_JITTER * quadSize;
                            float py5 = py3 + (random.nextFloat() * 2 - 1) * VERTEX_JITTER * quadSize;
                            float pz5 = pz3 + MIN_SEGMENT_LENGTH + random.nextFloat() * (MAX_SEGMENT_LENGTH - MIN_SEGMENT_LENGTH);

                            drawSegment(stack, tesselator, layer, px3, py3, pz3, px5, py5, pz5, THICKNESS * 0.6f * quadSize);
                        }

                        px3 = px4;
                        py3 = py4;
                        pz3 = pz4;
                    }
                }

                px = px2;
                py = py2;
                pz = pz2;
            }

            // The final segment lands exactly on the target (or jitters
            // freely at the end of an open-ended arc).
            float px2 = freeEnd ? (random.nextFloat() * 2 - 1) * VERTEX_JITTER * quadSize : 0;
            float py2 = freeEnd ? (random.nextFloat() * 2 - 1) * VERTEX_JITTER * quadSize : 0;
            drawSegment(stack, tesselator, layer, px, py, pz, px2, py2, length, THICKNESS * quadSize);
        }

        RenderSystem.disableBlend();
    }


    @Override
    protected boolean shouldApplyOriginOffset() {
        // When SpellFx links an arc to its caster/previous target, the packet already carries the
        // exact hand/centre offset.  DsParticleTargeted's generic +1.2-block axial offset is useful
        // for older entity-linked beams, but would move authored lightning away from the hand.
        return false;
    }

    private void drawSegment(PoseStack stack, Tesselator tesselator, int layer, float x1, float y1, float z1, float x2, float y2, float z2, float thickness) {
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        switch (layer) {
            case 0 -> drawShearedBox(stack, buffer, x1, y1, z1, x2, y2, z2, 0.25f * thickness, 1, 1, 1, 1);
            case 1 -> drawShearedBox(stack, buffer, x1, y1, z1, x2, y2, z2, 0.6f * thickness, (rCol + 1) / 2, (gCol + 1) / 2, (bCol + 1) / 2, 0.65f);
            case 2 -> drawShearedBox(stack, buffer, x1, y1, z1, x2, y2, z2, thickness, rCol, gCol, bCol, 0.3f);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }
    }

    private void drawShearedBox(PoseStack stack, BufferBuilder buffer, float x1, float y1, float z1, float x2, float y2, float z2, float width, float r, float g, float b, float a) {
        var pose = stack.last().pose();
        buffer.addVertex(pose, x1 - width, y1 - width, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2 - width, y2 - width, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x1 - width, y1 + width, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2 - width, y2 + width, z2).setColor(r, g, b, a);

        buffer.addVertex(pose, x1 + width, y1 + width, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2 + width, y2 + width, z2).setColor(r, g, b, a);

        buffer.addVertex(pose, x1 + width, y1 - width, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2 + width, y2 - width, z2).setColor(r, g, b, a);

        buffer.addVertex(pose, x1 - width, y1 - width, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2 - width, y2 - width, z2).setColor(r, g, b, a);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet spriteSet;

        public Provider(SpriteSet sprites) {
            spriteSet = sprites;
        }

        public static DsParticle createParticle(ClientLevel level, Vec3 pos) {
            return new LightningParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new LightningParticle(level, x, y, z, spriteSet);
        }
    }
}
