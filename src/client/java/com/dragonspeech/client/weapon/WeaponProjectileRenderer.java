package com.dragonspeech.client.weapon;

import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.weapon.ToolType;
import com.dragonspeech.weapon.WeaponProjectileEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Renders a hurled weapon as its REAL 3D item model, oriented to point
 * along its flight path and embed tip-first - the same visual idea
 * vanilla's ThrownTridentRenderer uses for the Trident, except built on
 * ItemRenderer's normal item-rendering path instead of a bespoke
 * hand-modeled TridentModel (this project has no art pipeline to author
 * a custom model/texture for every material+tool combination, so it
 * leans on the item models Minecraft already ships for every tool).
 *
 * Previously this used vanilla's ThrownItemRenderer, which BILLBOARDS
 * the item (always faces the camera, like a snowball/egg icon) - fine
 * for round throwables, wrong for a sword that should visibly point
 * where it's going. This renderer instead rotates the POSE STACK to
 * match the entity's own yRot/xRot (which AbstractArrow's tick() already
 * continuously sets from the real velocity vector - see AbstractArrow's
 * decompiled tick(), the `this.setYRot(...)`/`this.setXRot(...)` calls
 * driven by getDeltaMovement()), then draws the item inside that rotated
 * space - exactly the rotate-then-draw structure ThrownTridentRenderer
 * itself uses, just with ItemRenderer.renderStatic(...) standing in for
 * the custom TridentModel.
 *
 * VERSION-RISK / VISUAL-TUNING NOTE: the base Y/Z rotation below
 * (matching ThrownTridentRenderer's own approach) reliably points local
 * +Z along the flight direction - that part is standard, stable vanilla
 * math and is NOT the thing to change. What ISN'T verifiable without
 * actually seeing this render is which ItemDisplayContext + extra offset
 * combo makes a given tool's own baked model line up with that axis -
 * item models are authored per-item and vary, and each ItemDisplayContext
 * (GROUND, FIXED, ...) applies ITS OWN baked transform on top of ours
 * before our rotation is even applied, so the wrong context can fight
 * our rotation outright rather than just being slightly off.
 *
 * FIRST ATTEMPT used ItemDisplayContext.GROUND + a +90 X pitch, and
 * still came out lying flat (like a dropped item viewed from above)
 * rather than point-first - consistent with GROUND's own baked "lying
 * flat" transform dominating over our rotation.
 *
 * SECOND ATTEMPT (this version) switched to ItemDisplayContext.FIXED
 * and got concrete, precise feedback: shot straight down, the sword's
 * point ended up facing UP (and slightly back) - i.e. exactly the
 * opposite of correct, not a small angle error. That's the signature of
 * a clean 180-degree flip on the pitch axis, not a wrong-axis or
 * wrong-context problem - so rather than bolting on ANOTHER separate
 * rotation call (which, applied AFTER the yaw/pitch as a local-frame
 * roll, was the wrong way to express "flip the same axis" - see the old
 * EXTRA_PITCH_DEGREES / Axis.XP call this replaced), PITCH_FLIP_DEGREES
 * is now added DIRECTLY into the pitch rotation amount itself
 * (Axis.ZP.rotationDegrees(pitch + PITCH_FLIP_DEGREES)), guaranteeing a
 * true up-becomes-down flip in the SAME frame the base rotation
 * establishes, set to 180 as the fix for exactly this symptom.
 *
 * SELF-SERVE TUNING GUIDE if it's still not right after this:
 *   - Still upside-down / backwards -> the flip didn't apply right;
 *     double check PITCH_FLIP_DEGREES is actually 180 below.
 *   - Right end pointing the right way, but the WHOLE thing is rotated
 *     around its own long axis (blade flat-on to the camera when it
 *     should be edge-on, or vice versa, or upside down along its own
 *     axis) -> that's ROLL, not pitch - step EXTRA_ROLL_DEGREES through
 *     90/180/270.
 *   - Still lying flat regardless of flip -> ItemDisplayContext.FIXED is
 *     ALSO baking in its own transform; try ItemDisplayContext.NONE
 *     instead (no baked transform at all, so our rotation becomes the
 *     ONLY thing controlling orientation).
 */
public class WeaponProjectileRenderer extends EntityRenderer<WeaponProjectileEntity> {

    /** Visual tuning knobs - see class doc's self-serve guide. */
    private static final ItemDisplayContext DISPLAY_CONTEXT = ItemDisplayContext.FIXED;
    private static final float PITCH_FLIP_DEGREES = 180.0f;
    /** Small residual correction - the big 180 flip is right (point no longer faces the wrong way entirely), this is for a SLIGHT remaining downward lean. +30 was close per direct feedback; stepping to +40. If this overshoots (tilts upward now), the true value is between 30 and 40. */
    private static final float PITCH_TRIM_DEGREES = 40.0f;
    private static final float EXTRA_ROLL_DEGREES = 0.0f;

    /** Items render quite small at their natural item-model scale; this is a flight-visibility bump, not a gameplay value. */
    private static final float RENDER_SCALE = 1.1f;

    private final ItemRenderer itemRenderer;

    public WeaponProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(WeaponProjectileEntity entity, float entityYaw, float partialTicks,
                        PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // Same rotate-to-flight-path structure as vanilla's
        // ThrownTridentRenderer: yaw first (offset -90 so local +Z, not
        // +X, ends up forward), then pitch - with PITCH_FLIP_DEGREES
        // folded directly into the pitch amount (see class doc for why
        // that's added here and not as a separate rotation call).
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + PITCH_FLIP_DEGREES + PITCH_TRIM_DEGREES));
        if (EXTRA_ROLL_DEGREES != 0f) {
            // Roll around the item's own now-established forward axis
            // (local Z, post yaw+pitch) - NOT the same call as the old
            // pitch-tuning attempt, which rotated around local X after
            // pitch and so behaved more like an unpredictable diagonal
            // wobble than a clean roll.
            poseStack.mulPose(Axis.ZP.rotationDegrees(EXTRA_ROLL_DEGREES));
        }
        poseStack.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);

        MagicAffinity affinity = entity.magicAffinity();
        if (entity.isMagicalConstruct()) {
            // A conjured elemental/conceptual weapon is not a recoloured vanilla gold/diamond
            // item.  Its spoken substance is the model: ice, fire, force, time, gravity, etc.
            drawMagicWeapon(poseStack.last().pose(), entity.toolType(), affinity);
        } else {
            ItemStack stack = entity.getItem();
            if (!stack.isEmpty()) {
                itemRenderer.renderStatic(
                    stack,
                    DISPLAY_CONTEXT,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    buffer,
                    entity.level(),
                    entity.getId()
                );
            }
            // A real weapon drawn with `taka` can still be wrapped in a spoken affinity. Keep the
            // real item visible and add a restrained magical edge instead of replacing its matter.
            if (affinity != MagicAffinity.ARCANE) {
                drawMagicEdge(poseStack.last().pose(), entity.toolType(), affinity);
            }
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void drawMagicWeapon(Matrix4f pose, ToolType type, MagicAffinity affinity) {
        float[] c = unpack(affinity.color());
        float[] f = unpack(affinity.fadeColor());
        beginMagic();
        drawWeaponGeometry(pose, type, c[0], c[1], c[2], .82f, 1.00f);
        drawWeaponGeometry(pose, type, f[0], f[1], f[2], .38f, .72f);
        endMagic();
    }

    private static void drawMagicEdge(Matrix4f pose, ToolType type, MagicAffinity affinity) {
        float[] c = unpack(affinity.color());
        beginMagic();
        drawWeaponGeometry(pose, type, c[0], c[1], c[2], .28f, 1.14f);
        endMagic();
    }

    private static void beginMagic() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
    }

    private static void endMagic() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Compact geometry-only weapon silhouettes.  The tool word chooses shape while MagicAffinity
     * chooses substance, so `vopnbinda is sverd seida` is genuinely an ice sword and not a gold
     * sword with an ice particle trail pasted over it.
     */
    private static void drawWeaponGeometry(Matrix4f pose, ToolType type, float r, float g, float b, float a, float s) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder vb = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        switch (type) {
            case SWORD -> {
                box(vb, pose, -.075f*s, -.055f*s, -.72f*s, .075f*s, .055f*s, .35f*s, r,g,b,a);
                pyramidTip(vb, pose, .075f*s, .055f*s, -.92f*s, -.72f*s, r,g,b,a);
                box(vb, pose, -.28f*s, -.065f*s, .30f*s, .28f*s, .065f*s, .40f*s, r,g,b,a);
                box(vb, pose, -.055f*s, -.055f*s, .36f*s, .055f*s, .055f*s, .76f*s, r,g,b,a);
            }
            case AXE -> {
                box(vb, pose, -.045f*s,-.045f*s,-.18f*s,.045f*s,.045f*s,.78f*s,r,g,b,a);
                box(vb, pose, -.36f*s,-.07f*s,-.58f*s,.12f*s,.07f*s,-.18f*s,r,g,b,a);
                pyramidSide(vb, pose, -.50f*s, -.36f*s, -.58f*s, -.18f*s, r,g,b,a);
            }
            case PICKAXE -> {
                box(vb, pose, -.045f*s,-.045f*s,-.15f*s,.045f*s,.045f*s,.80f*s,r,g,b,a);
                box(vb, pose, -.42f*s,-.055f*s,-.45f*s,.42f*s,.055f*s,-.28f*s,r,g,b,a);
            }
            case SHOVEL -> {
                box(vb, pose, -.04f*s,-.04f*s,-.04f*s,.04f*s,.04f*s,.82f*s,r,g,b,a);
                box(vb, pose, -.19f*s,-.07f*s,-.55f*s,.19f*s,.07f*s,-.05f*s,r,g,b,a);
                pyramidTip(vb, pose, .19f*s,.07f*s,-.72f*s,-.55f*s,r,g,b,a);
            }
            case HOE -> {
                box(vb, pose, -.04f*s,-.04f*s,-.12f*s,.04f*s,.04f*s,.82f*s,r,g,b,a);
                box(vb, pose, -.05f*s,-.055f*s,-.43f*s,.38f*s,.055f*s,-.25f*s,r,g,b,a);
            }
            case TRIDENT -> {
                box(vb, pose, -.035f*s,-.035f*s,-.62f*s,.035f*s,.035f*s,.82f*s,r,g,b,a);
                for (float x : new float[]{-.18f,0f,.18f}) {
                    box(vb, pose,(x-.028f)*s,-.028f*s,-.82f*s,(x+.028f)*s,.028f*s,-.55f*s,r,g,b,a);
                    pyramidTipOffset(vb, pose,x*s,.03f*s,-1.02f*s,-.82f*s,r,g,b,a);
                }
                box(vb, pose,-.22f*s,-.035f*s,-.60f*s,.22f*s,.035f*s,-.51f*s,r,g,b,a);
            }
        }
        MeshData mesh = vb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void box(BufferBuilder b, Matrix4f m, float x0,float y0,float z0,float x1,float y1,float z1,
                            float r,float g,float bl,float a) {
        tri(b,m,x0,y0,z0, x1,y0,z0, x1,y1,z0,r,g,bl,a); tri(b,m,x0,y0,z0, x1,y1,z0, x0,y1,z0,r,g,bl,a);
        tri(b,m,x1,y0,z1, x0,y0,z1, x0,y1,z1,r,g,bl,a); tri(b,m,x1,y0,z1, x0,y1,z1, x1,y1,z1,r,g,bl,a);
        tri(b,m,x0,y0,z1, x0,y0,z0, x0,y1,z0,r,g,bl,a); tri(b,m,x0,y0,z1, x0,y1,z0, x0,y1,z1,r,g,bl,a);
        tri(b,m,x1,y0,z0, x1,y0,z1, x1,y1,z1,r,g,bl,a); tri(b,m,x1,y0,z0, x1,y1,z1, x1,y1,z0,r,g,bl,a);
        tri(b,m,x0,y1,z0, x1,y1,z0, x1,y1,z1,r,g,bl,a); tri(b,m,x0,y1,z0, x1,y1,z1, x0,y1,z1,r,g,bl,a);
        tri(b,m,x0,y0,z1, x1,y0,z1, x1,y0,z0,r,g,bl,a); tri(b,m,x0,y0,z1, x1,y0,z0, x0,y0,z0,r,g,bl,a);
    }

    private static void pyramidTip(BufferBuilder b, Matrix4f m,float hx,float hy,float tipZ,float baseZ,float r,float g,float bl,float a) {
        pyramidTipOffset(b,m,0,hx,tipZ,baseZ,r,g,bl,a);
    }
    private static void pyramidTipOffset(BufferBuilder b, Matrix4f m,float cx,float h,float tipZ,float baseZ,float r,float g,float bl,float a) {
        tri(b,m,cx,0,tipZ,cx-h,-h,baseZ,cx+h,-h,baseZ,r,g,bl,a);
        tri(b,m,cx,0,tipZ,cx+h,-h,baseZ,cx+h,h,baseZ,r,g,bl,a);
        tri(b,m,cx,0,tipZ,cx+h,h,baseZ,cx-h,h,baseZ,r,g,bl,a);
        tri(b,m,cx,0,tipZ,cx-h,h,baseZ,cx-h,-h,baseZ,r,g,bl,a);
    }
    private static void pyramidSide(BufferBuilder b, Matrix4f m,float tipX,float baseX,float z0,float z1,float r,float g,float bl,float a) {
        tri(b,m,tipX,0,(z0+z1)/2,baseX,-.07f,z0,baseX,.07f,z0,r,g,bl,a);
        tri(b,m,tipX,0,(z0+z1)/2,baseX,.07f,z0,baseX,.07f,z1,r,g,bl,a);
        tri(b,m,tipX,0,(z0+z1)/2,baseX,.07f,z1,baseX,-.07f,z1,r,g,bl,a);
        tri(b,m,tipX,0,(z0+z1)/2,baseX,-.07f,z1,baseX,-.07f,z0,r,g,bl,a);
    }
    private static void tri(BufferBuilder b, Matrix4f m,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,
                            float r,float g,float bl,float a) {
        b.addVertex(m,ax,ay,az).setColor(r,g,bl,a);
        b.addVertex(m,bx,by,bz).setColor(r,g,bl,a);
        b.addVertex(m,cx,cy,cz).setColor(r,g,bl,a);
    }
    private static float[] unpack(int c) {
        return new float[]{((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f};
    }

    @Override
    public ResourceLocation getTextureLocation(WeaponProjectileEntity entity) {
        // Never actually sampled - render() above draws the item's own
        // baked textures via ItemRenderer, not a texture bound directly
        // through this method. Needs to return SOMETHING valid in case
        // any other vanilla code path queries it.
        return MissingTextureAtlasSprite.getLocation();
    }
}
