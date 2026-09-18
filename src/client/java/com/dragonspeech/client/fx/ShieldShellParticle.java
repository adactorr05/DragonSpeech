package com.dragonspeech.client.fx;

import com.dragonspeech.entity.MagicBarrierEntity;
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
 * The actual "visible shield" fix, take two. The first version rendered
 * correctly but was genuinely too faint to read as a shield - two real
 * causes, both fixed here:
 *
 * 1. The fill was drawn with ADDITIVE blending (SRC_ALPHA, ONE) - the
 *    same mode used for glows/sparks/beams. Additive blending only ever
 *    ADDS light; against a bright sky or pale terrain a low-alpha
 *    additive surface barely registers, because there's nothing for it
 *    to visibly darken or tint. The fill now uses ordinary alpha
 *    blending (SRC_ALPHA, ONE_MINUS_SRC_ALPHA), which actually reads as
 *    a translucent coloured surface the way glass or a soap bubble does.
 * 2. The base alpha itself was low (0.30, meant to feel subtle). Bumped
 *    to a properly visible 0.5 base, and every vertex now gets an
 *    additional FRESNEL-style rim boost - the silhouette edges of the
 *    sphere (as seen from wherever the camera currently is) render
 *    noticeably brighter than the centre, which is the actual visual
 *    signature of an energy-shield look, not just "translucent ball."
 *
 * The seam-line overlay keeps its additive blend on top of the new fill
 * - that part was already working as intended (bright glowing panel
 * edges), it just needed a properly visible surface underneath it.
 */
public class ShieldShellParticle extends DsParticle {

    private static final float BASE_ALPHA = 0.50f;
    private static final float PULSE_AMPLITUDE = 0.10f;
    private static final float PULSE_SPEED = 0.05f;
    private static final float RIM_BOOST = 0.45f; // added on top of BASE_ALPHA at grazing/silhouette angles
    private static final float SEAM_ALPHA = 0.75f;

    public ShieldShellParticle(ClientLevel world, double x, double y, double z, SpriteSet spriteSet) {
        super(world, x, y, z, spriteSet, false);
        this.setColor(0.25f, 0.65f, 1f);
        this.lifetime = 200;
        this.hasPhysics = false;
        this.gravity = 0;
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        PoseStack stack = new PoseStack();

        updateEntityLinking(tickDelta);

        float x = (float) (this.xo + (this.x - this.xo) * (double) tickDelta);
        float y = (float) (this.yo + (this.y - this.yo) * (double) tickDelta);
        float z = (float) (this.zo + (this.z - this.zo) * (double) tickDelta);

        double liveRadius = this.quadSize * 10.0;
        boolean flat = false;
        float facingYaw = 0f;
        if (this.entity instanceof MagicBarrierEntity barrier) {
            liveRadius = barrier.radius();
            flat = barrier.flat();
            facingYaw = barrier.facingYaw();
        }

        float pulse = Mth.sin((this.age + tickDelta) * PULSE_SPEED) * PULSE_AMPLITUDE;
        float fillAlpha = Math.max(0.1f, BASE_ALPHA + pulse);
        float seamAlpha = Math.max(0.15f, SEAM_ALPHA + pulse);

        // Camera position in this particle's LOCAL (sphere-centred) space -
        // needed per-vertex for the rim/fresnel boost below.
        Vec3 cameraLocal = new Vec3(camera.getPosition().x - x, camera.getPosition().y - y, camera.getPosition().z - z);

        stack.pushPose();
        stack.translate(x - camera.getPosition().x, y - camera.getPosition().y, z - camera.getPosition().z);

        RenderSystem.enableCull();
        RenderSystem.depthMask(false);

        Tesselator tesselator = Tesselator.getInstance();

        // ---- Fill: ordinary alpha blend, so it reads as a solid tinted surface ----
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (!flat) {
            drawSphereShell(stack, tesselator, (float) liveRadius, cameraLocal, rCol, gCol, bCol, fillAlpha);
        } else {
            drawWallShell(stack, tesselator, (float) liveRadius, facingYaw, rCol, gCol, bCol, fillAlpha);
        }

        // ---- Seams: additive, for a bright glowing-panel-edge accent ----
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        if (!flat) {
            drawSphereSeams(stack, tesselator, (float) liveRadius, rCol, gCol, bCol, seamAlpha);
        } else {
            drawWallSeams(stack, tesselator, (float) liveRadius, facingYaw, rCol, gCol, bCol, seamAlpha);
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        stack.popPose();
        restoreSheetState();
    }

    // ============================== Bubble ==============================

    private static void drawSphereShell(PoseStack stack, Tesselator tesselator, float radius, Vec3 cameraLocal, float r, float g, float b, float baseAlpha) {
        float latStep = (float) Math.PI / 16;
        float longStep = (float) Math.PI / 16;
        drawSphereBand(stack, tesselator, radius, latStep, longStep, true, cameraLocal, r, g, b, baseAlpha);
        drawSphereBand(stack, tesselator, radius, latStep, longStep, false, cameraLocal, r, g, b, baseAlpha);
    }

    /** Per-vertex fresnel: alpha rises toward RIM_BOOST at grazing view angles (the sphere's silhouette) and falls back to baseAlpha where the surface faces the camera directly - the actual visual signature of an energy shield. */
    private static float rimAlpha(float vx, float vy, float vz, float radius, Vec3 cameraLocal, float baseAlpha) {
        float nx = vx / radius, ny = vy / radius, nz = vz / radius; // outward normal (sphere centred at local origin)
        Vec3 viewDir = new Vec3(cameraLocal.x - vx, cameraLocal.y - vy, cameraLocal.z - vz);
        double len = viewDir.length();
        if (len < 0.0001) {
            return baseAlpha;
        }
        double facing = Math.abs((viewDir.x / len) * nx + (viewDir.y / len) * ny + (viewDir.z / len) * nz);
        float rim = (float) (1.0 - facing); // 0 = facing camera directly, 1 = grazing/silhouette
        return Math.min(1f, baseAlpha + RIM_BOOST * rim);
    }

    private static void drawSphereBand(PoseStack stack, Tesselator tesselator, float radius, float latStep, float longStep, boolean inside, Vec3 cameraLocal, float r, float g, float b, float baseAlpha) {
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        var pose = stack.last().pose();

        boolean goingUp = inside;
        float poleY = goingUp ? -radius : radius;
        buffer.addVertex(pose, 0, poleY, 0).setColor(r, g, b, rimAlpha(0, poleY, 0, radius, cameraLocal, baseAlpha));

        for (float longitude = -(float) Math.PI; longitude <= (float) Math.PI; longitude += longStep) {
            for (float theta = (float) Math.PI / 2 - latStep; theta >= -(float) Math.PI / 2 + latStep; theta -= latStep) {
                float latitude = goingUp ? -theta : theta;
                float hRadius = radius * Mth.cos(latitude);
                float vy = radius * Mth.sin(latitude);

                float vx = hRadius * Mth.sin(longitude);
                float vz = hRadius * Mth.cos(longitude);
                buffer.addVertex(pose, vx, vy, vz).setColor(r, g, b, rimAlpha(vx, vy, vz, radius, cameraLocal, baseAlpha));

                vx = hRadius * Mth.sin(longitude + longStep);
                vz = hRadius * Mth.cos(longitude + longStep);
                buffer.addVertex(pose, vx, vy, vz).setColor(r, g, b, rimAlpha(vx, vy, vz, radius, cameraLocal, baseAlpha));
            }
            float endY = goingUp ? radius : -radius;
            buffer.addVertex(pose, 0, endY, 0).setColor(r, g, b, rimAlpha(0, endY, 0, radius, cameraLocal, baseAlpha));
            goingUp = !goingUp;
        }

        upload(buffer);
    }

    private static void drawSphereSeams(PoseStack stack, Tesselator tesselator, float radius, float r, float g, float b, float a) {
        var pose = stack.last().pose();
        int rings = 6;

        for (int i = 0; i < rings; i++) {
            float longitude = (float) (Math.PI * 2 * i / rings);
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int s = 0; s <= 32; s++) {
                float t = (float) (Math.PI * 2 * s / 32);
                float vx = radius * Mth.sin(t) * Mth.cos(longitude);
                float vy = radius * Mth.cos(t);
                float vz = radius * Mth.sin(t) * Mth.sin(longitude);
                buffer.addVertex(pose, vx, vy, vz).setColor(r, g, b, a);
            }
            upload(buffer);
        }

        for (int i = 1; i < 3; i++) {
            float latitude = (float) (Math.PI / 2 * i / 3 - Math.PI / 4);
            float ringRadius = radius * Mth.cos(latitude);
            float y = radius * Mth.sin(latitude);
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int s = 0; s <= 32; s++) {
                float t = (float) (Math.PI * 2 * s / 32);
                buffer.addVertex(pose, ringRadius * Mth.cos(t), y, ringRadius * Mth.sin(t)).setColor(r, g, b, a);
            }
            upload(buffer);
        }
    }

    // ============================== Wall ==============================

    private static void drawWallShell(PoseStack stack, Tesselator tesselator, float radius, float facingYawDeg, float r, float g, float b, float a) {
        float yawRad = (float) Math.toRadians(facingYawDeg);
        float sideX = Mth.cos(yawRad);
        float sideZ = Mth.sin(yawRad);

        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        var pose = stack.last().pose();

        buffer.addVertex(pose, -sideX * radius, -radius, -sideZ * radius).setColor(r, g, b, a);
        buffer.addVertex(pose, -sideX * radius, radius, -sideZ * radius).setColor(r, g, b, a);
        buffer.addVertex(pose, sideX * radius, -radius, sideZ * radius).setColor(r, g, b, a);
        buffer.addVertex(pose, sideX * radius, radius, sideZ * radius).setColor(r, g, b, a);

        upload(buffer);
    }

    private static void drawWallSeams(PoseStack stack, Tesselator tesselator, float radius, float facingYawDeg, float r, float g, float b, float a) {
        float yawRad = (float) Math.toRadians(facingYawDeg);
        float sideX = Mth.cos(yawRad);
        float sideZ = Mth.sin(yawRad);
        var pose = stack.last().pose();

        // Bright border around the whole wall - the same rim-glow idea as
        // the sphere's fresnel, approximated here as a simple perimeter
        // outline since a flat quad doesn't have the sphere's silhouette
        // variation to compute a real per-vertex rim from.
        BufferBuilder border = tesselator.begin(VertexFormat.Mode.LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        border.addVertex(pose, -sideX * radius, -radius, -sideZ * radius).setColor(r, g, b, a);
        border.addVertex(pose, -sideX * radius, radius, -sideZ * radius).setColor(r, g, b, a);
        border.addVertex(pose, sideX * radius, radius, sideZ * radius).setColor(r, g, b, a);
        border.addVertex(pose, sideX * radius, -radius, sideZ * radius).setColor(r, g, b, a);
        border.addVertex(pose, -sideX * radius, -radius, -sideZ * radius).setColor(r, g, b, a);
        upload(border);

        int lines = 4;
        for (int i = 1; i < lines; i++) {
            float w = -radius + (radius * 2f * i / lines);
            BufferBuilder vertical = tesselator.begin(VertexFormat.Mode.LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            vertical.addVertex(pose, sideX * w, -radius, sideZ * w).setColor(r, g, b, a * 0.6f);
            vertical.addVertex(pose, sideX * w, radius, sideZ * w).setColor(r, g, b, a * 0.6f);
            upload(vertical);

            BufferBuilder horizontal = tesselator.begin(VertexFormat.Mode.LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            horizontal.addVertex(pose, -sideX * radius, w, -sideZ * radius).setColor(r, g, b, a * 0.6f);
            horizontal.addVertex(pose, sideX * radius, w, sideZ * radius).setColor(r, g, b, a * 0.6f);
            upload(horizontal);
        }
    }

    private static void upload(BufferBuilder buffer) {
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
            return new ShieldShellParticle(level, pos.x, pos.y, pos.z, spriteSet);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ShieldShellParticle(level, x, y, z, spriteSet);
        }
    }
}