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
 * Renders hurled weapons as actual 3-D objects rather than billboarded item icons.
 *
 * IMPORTANT AXIS RULE: Minecraft's arrow-style projectile transform (yaw - 90, then pitch around Z)
 * points the model's LOCAL +X axis down the entity velocity vector. Dragon Speech's holographic
 * weapon geometry is authored lengthwise on LOCAL +Z (handle at -Z, business end/tip at +Z).
 * Therefore conjured geometry needs one +90 degree local-Y correction after the projectile yaw/pitch
 * so authored +Z becomes renderer-forward +X. The older implementation tried repeated 180-degree
 * flips while leaving that 90-degree axis mismatch intact, which is why the sword alternated between
 * point-left/hilt-right and hilt-left/point-right instead of actually pointing along its flight path.
 *
 * Real vanilla item models still use the older FIXED-context compensation because their baked model
 * transforms are unrelated to the custom holographic geometry.
 */
public class WeaponProjectileRenderer extends EntityRenderer<WeaponProjectileEntity> {

    private static final ItemDisplayContext DISPLAY_CONTEXT = ItemDisplayContext.FIXED;
    /** Custom geometry is +Z-long, while arrow-style projectile math expects +X-long. */
    private static final float CONJURED_GEOMETRY_TO_FORWARD_DEGREES = 90.0f;
    /** Keep the pre-existing baked-item compensation only for real vanilla items drawn with `taka`. */
    private static final float REAL_ITEM_PITCH_FLIP_DEGREES = 180.0f;
    private static final float REAL_ITEM_PITCH_TRIM_DEGREES = 40.0f;
    private static final float REAL_ITEM_ROLL_DEGREES = 180.0f;

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

        float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

        // Vanilla arrow-style orientation: after these two rotations LOCAL +X is flight-forward.
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));

        MagicAffinity affinity = entity.magicAffinity();
        if (entity.isMagicalConstruct()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
            // Our authored weapon axis is +Z, so map +Z -> +X. This is the missing 90-degree
            // correction that repeated 180-degree flips could never solve.
            poseStack.mulPose(Axis.YP.rotationDegrees(CONJURED_GEOMETRY_TO_FORWARD_DEGREES));
            poseStack.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
            drawMagicWeapon(poseStack.last().pose(), entity.toolType(), affinity);
        } else {
            // Vanilla item models bring their own baked FIXED transform, so retain their separate
            // compensation path rather than applying the custom-geometry axis correction to them.
            poseStack.mulPose(Axis.ZP.rotationDegrees(pitch + REAL_ITEM_PITCH_FLIP_DEGREES + REAL_ITEM_PITCH_TRIM_DEGREES));
            if (REAL_ITEM_ROLL_DEGREES != 0f) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(REAL_ITEM_ROLL_DEGREES));
            }
            poseStack.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
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

    public static void drawMagicWeapon(Matrix4f pose, ToolType type, MagicAffinity affinity) {
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
                // Geometry is authored handle-at--Z / tip-at-+Z. The renderer maps this local
                // +Z weapon axis onto Minecraft projectile-forward (+X) before drawing it.
                box(vb, pose, -.075f*s, -.055f*s, -.35f*s, .075f*s, .055f*s, .72f*s, r,g,b,a);
                pyramidTip(vb, pose, .075f*s, .055f*s, .92f*s, .72f*s, r,g,b,a);
                box(vb, pose, -.28f*s, -.065f*s, -.40f*s, .28f*s, .065f*s, -.30f*s, r,g,b,a);
                box(vb, pose, -.055f*s, -.055f*s, -.76f*s, .055f*s, .055f*s, -.36f*s, r,g,b,a);
            }
            case AXE -> {
                // Every conjured weapon uses the same authored axis as SWORD: business end at +Z,
                // grip/handle trailing at -Z. This prevents each tool from needing its own renderer flip.
                box(vb, pose, -.045f*s,-.045f*s,-.78f*s,.045f*s,.045f*s,.18f*s,r,g,b,a);
                box(vb, pose, -.36f*s,-.07f*s,.18f*s,.12f*s,.07f*s,.58f*s,r,g,b,a);
                pyramidSide(vb, pose, -.50f*s, -.36f*s, .18f*s, .58f*s, r,g,b,a);
            }
            case PICKAXE -> {
                box(vb, pose, -.045f*s,-.045f*s,-.80f*s,.045f*s,.045f*s,.15f*s,r,g,b,a);
                box(vb, pose, -.42f*s,-.055f*s,.28f*s,.42f*s,.055f*s,.45f*s,r,g,b,a);
            }
            case SHOVEL -> {
                box(vb, pose, -.04f*s,-.04f*s,-.82f*s,.04f*s,.04f*s,.04f*s,r,g,b,a);
                box(vb, pose, -.19f*s,-.07f*s,.05f*s,.19f*s,.07f*s,.55f*s,r,g,b,a);
                pyramidTip(vb, pose, .19f*s,.07f*s,.72f*s,.55f*s,r,g,b,a);
            }
            case HOE -> {
                box(vb, pose, -.04f*s,-.04f*s,-.82f*s,.04f*s,.04f*s,.12f*s,r,g,b,a);
                box(vb, pose, -.05f*s,-.055f*s,.25f*s,.38f*s,.055f*s,.43f*s,r,g,b,a);
            }
            case SPEAR -> {
                box(vb, pose, -.035f*s,-.035f*s,-.82f*s,.035f*s,.035f*s,.68f*s,r,g,b,a);
                pyramidTip(vb, pose, .12f*s,.12f*s,1.02f*s,.68f*s,r,g,b,a);
            }
            case TRIDENT -> {
                box(vb, pose, -.035f*s,-.035f*s,-.82f*s,.035f*s,.035f*s,.62f*s,r,g,b,a);
                for (float x : new float[]{-.18f,0f,.18f}) {
                    box(vb, pose,(x-.028f)*s,-.028f*s,.55f*s,(x+.028f)*s,.028f*s,.82f*s,r,g,b,a);
                    pyramidTipOffset(vb, pose,x*s,.03f*s,1.02f*s,.82f*s,r,g,b,a);
                }
                box(vb, pose,-.22f*s,-.035f*s,.51f*s,.22f*s,.035f*s,.60f*s,r,g,b,a);
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
