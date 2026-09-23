package com.dragonspeech.client.entity;

import com.dragonspeech.entity.BarrierShape;
import com.dragonspeech.entity.MagicBarrierEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * A real, solid UV-sphere shield, drawn directly on the entity - ported
 * from the reference EBW RenderForcefield.java the person provided
 * (same double-shell lat/long sphere: a bright near-white inner shell
 * plus a coloured outer shell, with a slow sine pulse), NOT the
 * particle-based approximation this used to be (ShieldShellParticle -
 * still present in the codebase but no longer spawned; see
 * MagicBarrierEntity.tick(), the spawnShellParticleIfNeeded() call was
 * removed there so this renderer is the only visual now, avoiding a
 * double-render).
 *
 * WHY THIS WAS SAFE TO WRITE WITH CONFIDENCE (unlike most of this
 * project's client-rendering guesses): EBW's code uses the old
 * pre-1.13 immediate-mode GL pipeline (GlStateManager.pushMatrix/
 * translate, Tessellator+BufferBuilder+GL11 constants), which doesn't
 * exist anymore and couldn't be ported as-is. But this project's OWN
 * ShieldShellParticle.java (still present, unused now) already
 * implements the modern 1.21.1 equivalent of exactly this kind of
 * direct sphere geometry - Tesselator.begin(VertexFormat.Mode.
 * TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR), addVertex(pose,
 * x,y,z).setColor(...), BufferUploader.drawWithShader(...), RenderSystem
 * blend/shader setup - and is presumably already proven to actually
 * compile and render in this exact project. Every low-level call below
 * is copied from that proven pattern; only the algorithm driving WHERE
 * the vertices go (EBW's simpler double-shell sphere, no fresnel rim)
 * and WHERE this runs (an EntityRenderer's render(), not a Particle's
 * render()) are different. The one thing that genuinely differs from
 * ShieldShellParticle and is unverified here: an EntityRenderer's
 * incoming PoseStack is already translated to the entity's world
 * position by EntityRenderDispatcher before render() is called (see its
 * decompiled source: poseStack.translate(...) happens right before
 * entityRenderer.render(...) is invoked) - so, unlike the particle
 * (which manually offsets every vertex by camera position itself),
 * drawing straight off poseStack.last().pose() at local (0,0,0) should
 * already be in the right place with no manual camera math needed. If
 * the sphere renders in the wrong spot (e.g. always at world origin),
 * that assumption about EntityRenderDispatcher's translate was wrong
 * and this needs the same manual camera-relative offset
 * ShieldShellParticle uses instead.
 */
public class MagicBarrierRenderer extends EntityRenderer<MagicBarrierEntity> {

    private static final float GROW_IN_TICKS = 3f;
    private static final float LAT_STEP = (float) Math.PI / 16;
    private static final float LONG_STEP = (float) Math.PI / 16;

    public MagicBarrierRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MagicBarrierEntity entity) {
        // Never sampled - this draws plain vertex-coloured geometry
        // (DefaultVertexFormat.POSITION_COLOR), no texture involved.
        return MissingTextureAtlasSprite.getLocation();
    }

    @Override
    public void render(MagicBarrierEntity entity, float entityYaw, float partialTicks,
                        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float age = entity.tickCount + partialTicks;
        float pulse = Mth.sin(age / 10f);

        float[] outer = unpackColor(entity.color());
        float[] inner = unpackColor(entity.fadeColor());

        float growth = age < GROW_IN_TICKS ? Math.max(0f, age / GROW_IN_TICKS) : 1f;
        float radius = (float) entity.radius() * growth;
        float alpha = 0.5f * growth;

        Matrix4f pose = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        // Explicitly re-asserted rather than assumed - "the shield
        // renders through walls/terrain" pointed straight at depth
        // testing somehow not being active for this draw. depthMask(false)
        // just below only stops THIS geometry from writing new depth
        // values (so overlapping translucent shells don't occlude each
        // other) - it does NOT disable testing against depth ALREADY in
        // the buffer from the terrain, which is what actually causes
        // occlusion by a wall. If depth test genuinely isn't the issue,
        // the more likely alternative is a draw-order/pass-timing
        // problem (this executing before terrain's depth is written for
        // the frame), which would need a different fix entirely.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        // Inner shell first (slightly smaller, near-white core - same as
        // EBW: drawSphere(radius-0.1-pulse, inside=true, r,g,b) then
        // drawSphere(same radius, inside=false, WHITE)), then the full
        // outer shell in the shield's actual colour. CUBE/WALL follow
        // the same three-pass idea (inner bright core, then two outer
        // passes) with their own geometry instead of a sphere - WALL's
        // geometry is a direct port of ShieldShellParticle's
        // drawWallShell, which already existed and worked for walls
        // before this class could render anything at all.
        float shrink = 0.1f + 0.025f * pulse;
        BarrierShape shape = entity.shape();
        if (shape == BarrierShape.CUBE) {
            drawCube(pose, tesselator, radius - shrink, true, outer[0], outer[1], outer[2], alpha);
            drawCube(pose, tesselator, radius - shrink, false, inner[0], inner[1], inner[2], alpha);
            drawCube(pose, tesselator, radius, false, outer[0], outer[1], outer[2], 0.7f * alpha);
        } else if (shape == BarrierShape.WALL) {
            // Keep a faint translucent pane for readable coverage, then overlay the Dragon Flux-style
            // edge-sharing honeycomb so frontal skjoldr barriers read as constructed hex plates.
            drawWall(pose, tesselator, radius, entity.facingYaw(), outer[0], outer[1], outer[2], 0.18f * alpha);
            drawHexWall(pose, tesselator, radius, entity.facingYaw(), outer[0], outer[1], outer[2], Math.min(0.95f, 1.55f * alpha));
        } else {
            drawSphere(pose, tesselator, radius - shrink, true, outer[0], outer[1], outer[2], alpha);
            drawSphere(pose, tesselator, radius - shrink, false, inner[0], inner[1], inner[2], alpha);
            drawSphere(pose, tesselator, radius, false, outer[0], outer[1], outer[2], 0.7f * alpha);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    private static float[] unpackColor(int packed) {
        return new float[] {
            ((packed >> 16) & 0xFF) / 255f,
            ((packed >> 8) & 0xFF) / 255f,
            (packed & 0xFF) / 255f
        };
    }

    /**
     * Direct port of EBW's drawSphere - same lat/long triangle-strip
     * walk, same "inside vs outside" pole-start trick, just modern
     * addVertex(pose,x,y,z).setColor(...) calls (no .endVertex(), and no
     * .color(r,g,b,a) name - see ShieldShellParticle for the same
     * pattern already used successfully in this project) instead of the
     * old buffer.pos(...).color(...).endVertex() chain.
     */
    private static void drawSphere(Matrix4f pose, Tesselator tesselator, float radius, boolean inside,
                                    float r, float g, float b, float a) {
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        boolean goingUp = inside;
        buffer.addVertex(pose, 0, goingUp ? -radius : radius, 0).setColor(r, g, b, a);

        for (float longitude = -(float) Math.PI; longitude <= (float) Math.PI; longitude += LONG_STEP) {
            for (float theta = (float) Math.PI / 2 - LAT_STEP; theta >= -(float) Math.PI / 2 + LAT_STEP; theta -= LAT_STEP) {
                float latitude = goingUp ? -theta : theta;

                float hRadius = radius * Mth.cos(latitude);
                float vy = radius * Mth.sin(latitude);
                float vx = hRadius * Mth.sin(longitude);
                float vz = hRadius * Mth.cos(longitude);
                buffer.addVertex(pose, vx, vy, vz).setColor(r, g, b, a);

                vx = hRadius * Mth.sin(longitude + LONG_STEP);
                vz = hRadius * Mth.cos(longitude + LONG_STEP);
                buffer.addVertex(pose, vx, vy, vz).setColor(r, g, b, a);
            }

            buffer.addVertex(pose, 0, goingUp ? radius : -radius, 0).setColor(r, g, b, a);
            goingUp = !goingUp;
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * A simple axis-aligned box, half-extent `radius` on every side
     * (matches how BarrierEffectHandler already sizes a CUBE shield -
     * same "radius" meaning half-side-length as a sphere's actual
     * radius, so growing/shrinking behaves identically regardless of
     * shape). Six independent quads, each its own 4-vertex triangle
     * strip and its own begin/upload pair - simpler and lower-risk than
     * trying to chain all six faces into one continuous strip with
     * degenerate bridging triangles, and ShieldShellParticle already
     * proves multiple begin/upload cycles per render call work fine in
     * this project (it does the same for its seam rings).
     */
    private static void drawCube(Matrix4f pose, Tesselator tesselator, float radius, boolean inside,
                                  float r, float g, float b, float a) {
        float s = inside ? -radius : radius; // flips winding so backface culling (disabled here anyway, but kept honest) reads correctly either way
        // +X, -X, +Y, -Y, +Z, -Z faces, each as 4 corners in strip order.
        drawQuad(pose, tesselator, r, g, b, a,
            s, -radius, -radius, s, radius, -radius, s, -radius, radius, s, radius, radius);
        drawQuad(pose, tesselator, r, g, b, a,
            -s, -radius, radius, -s, radius, radius, -s, -radius, -radius, -s, radius, -radius);
        drawQuad(pose, tesselator, r, g, b, a,
            -radius, s, -radius, radius, s, -radius, -radius, s, radius, radius, s, radius);
        drawQuad(pose, tesselator, r, g, b, a,
            -radius, -s, radius, radius, -s, radius, -radius, -s, -radius, radius, -s, -radius);
        drawQuad(pose, tesselator, r, g, b, a,
            radius, -radius, s, radius, radius, s, -radius, -radius, s, -radius, radius, s);
        drawQuad(pose, tesselator, r, g, b, a,
            -radius, -radius, -s, -radius, radius, -s, radius, -radius, -s, radius, radius, -s);
    }

    private static void drawQuad(Matrix4f pose, Tesselator tesselator, float r, float g, float b, float a,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float x3, float y3, float z3, float x4, float y4, float z4) {
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        buffer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * A single flat double-sided pane, facing `facingYawDeg` - direct
     * port of ShieldShellParticle's drawWallShell (same sideX/sideZ
     * construction from yaw), just with the modern addVertex chain
     * instead of that file's own already-modern (but particle-context)
     * version.
     */
    /** Dragon Flux-inspired pointy-top honeycomb overlay for frontal barrier walls. */
    private static void drawHexWall(Matrix4f pose, Tesselator tesselator, float radius, float facingYawDeg,
                                    float r, float g, float b, float a) {
        float yawRad = (float) Math.toRadians(facingYawDeg);
        float sideX = Mth.cos(yawRad), sideZ = Mth.sin(yawRad);
        float cell = Mth.clamp(radius * 0.20f, 0.22f, 0.52f);
        float sx = (float)Math.sqrt(3.0) * cell, sy = 1.5f * cell;
        int cols = Math.max(3, Math.min(18, (int)Math.ceil((radius * 2f) / sx) + 2));
        int rows = Math.max(3, Math.min(14, (int)Math.ceil((radius * 2f) / sy) + 2));
        BufferBuilder lines = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int row=-rows; row<=rows; row++) for (int col=-cols; col<=cols; col++) {
            float ox = col*sx + ((row & 1)==0 ? 0f : sx*0.5f), oy=row*sy;
            if (Math.abs(ox) > radius-cell*0.55f || Math.abs(oy) > radius-cell*0.72f) continue;
            float[] vx=new float[6], vy=new float[6], vz=new float[6];
            for(int i=0;i<6;i++){
                double ang=Math.PI/6.0+i*Math.PI/3.0; float u=ox+(float)Math.cos(ang)*cell;
                vx[i]=sideX*u; vy[i]=oy+(float)Math.sin(ang)*cell; vz[i]=sideZ*u;
            }
            for(int i=0;i<6;i++){ int j=(i+1)%6;
                lines.addVertex(pose,vx[i],vy[i],vz[i]).setColor(r,g,b,a);
                lines.addVertex(pose,vx[j],vy[j],vz[j]).setColor(r,g,b,a);
            }
        }
        MeshData mesh=lines.build(); if(mesh!=null) BufferUploader.drawWithShader(mesh);
    }

    private static void drawWall(Matrix4f pose, Tesselator tesselator, float radius, float facingYawDeg,
                                  float r, float g, float b, float a) {
        float yawRad = (float) Math.toRadians(facingYawDeg);
        float sideX = Mth.cos(yawRad);
        float sideZ = Mth.sin(yawRad);

        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(pose, -sideX * radius, -radius, -sideZ * radius).setColor(r, g, b, a);
        buffer.addVertex(pose, -sideX * radius, radius, -sideZ * radius).setColor(r, g, b, a);
        buffer.addVertex(pose, sideX * radius, -radius, sideZ * radius).setColor(r, g, b, a);
        buffer.addVertex(pose, sideX * radius, radius, sideZ * radius).setColor(r, g, b, a);
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
