package com.dragonspeech.client.dragon;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.dragon.DragonEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Ties DragonModel + DragonAnimator + DragonEntity together for actual
 * on-screen rendering, including the saddle overlay, the glow/emissive
 * eye layer, and the death-dissolve effect. Adapted from Dragon Mounts
 * Legacy (com.github.kay9.dragonmounts.client.DragonRenderer, GPL-3.0).
 *
 * REAL CHANGES from the original:
 *
 * 1. Per-breed dynamic model baking removed. DML bakes a SEPARATE
 *    DragonModel per breed at runtime, since NeoForge lets you
 *    register model layers after breeds finish loading. Fabric has no
 *    equivalent late-registration hook - see DragonModel's own doc for
 *    the full reasoning. Since every breed here currently uses
 *    DragonBreed.ModelVariation.STANDARD, this renderer just uses ONE
 *    model for every breed (bakeModels()/modelCache/getModel() from
 *    the original are gone, not stubbed). A future breed needing
 *    different geometry is real, additional work here too, not
 *    already handled.
 *
 * 2. Holder<DragonBreed> -> plain ResourceLocation breed ID throughout
 *    (matches DragonEntity.getBreedId(), not getBreedHolder()).
 *
 * 3. DEFAULT_TEXTURES falls back to the "fire" breed (this project has
 *    no DragonBreed.BuiltIn enum the original's default referenced -
 *    "end" in DML, arbitrarily chosen here to match the same fallback
 *    DragonEntity.readAdditionalSaveData() already uses for
 *    consistency, not for any deeper reason).
 *
 * Texture path convention kept IDENTICAL to Dragon Mounts Legacy's own
 * (textures/entity/dragon/<breedId>/{body,glow,saddle}.png) - this is
 * exactly why forest/ice/end/fire's actual texture files can be reused
 * directly without any renaming, and why void/lightning/gold's new
 * textures need to follow that same folder shape.
 */
public class DragonRenderer extends MobRenderer<DragonEntity, DragonModel> {
    public static final ModelLayerLocation MODEL_LOCATION = new ModelLayerLocation(DragonSpeech.id("dragon"), "main");
    private static final ResourceLocation[] DEFAULT_TEXTURES = computeTextureCacheFor(DragonSpeech.id("fire"));
    private static final ResourceLocation DISSOLVE_TEXTURE = DragonSpeech.id("textures/entity/dragon/dissolve.png");
    private static final int LAYER_BODY = 0;
    private static final int LAYER_GLOW = 1;
    private static final int LAYER_SADDLE = 2;

    private final Map<ResourceLocation, ResourceLocation[]> textureCache = new HashMap<>(8);

    public DragonRenderer(EntityRendererProvider.Context modelBakery) {
        super(modelBakery, new DragonModel(modelBakery.bakeLayer(MODEL_LOCATION)), 2);

        addLayer(GLOW_LAYER);
        addLayer(SADDLE_LAYER);
        addLayer(DEATH_LAYER);
        addLayer(DIVE_TRAIL_LAYER);
    }

    @Override
    public boolean shouldRender(DragonEntity dragon, Frustum pCamera, double pCamX, double pCamY, double pCamZ) {
        return dragon.getBreed() != null && super.shouldRender(dragon, pCamera, pCamX, pCamY, pCamZ);
    }

    // During death, do not use the standard rendering and let the death layer handle it. Hacky, but better than mixins (kept from the original's own reasoning).
    @Nullable
    @Override
    protected RenderType getRenderType(DragonEntity entity, boolean visible, boolean invisToClient, boolean glowing) {
        return entity.deathTime > 0 ? null : super.getRenderType(entity, visible, invisToClient, glowing);
    }

    @Override
    public ResourceLocation getTextureLocation(DragonEntity dragon) {
        return getTextureForLayer(dragon.getBreedId(), LAYER_BODY);
    }

    public ResourceLocation getTextureForLayer(@Nullable ResourceLocation breedId, int layer) {
        if (breedId == null) {
            return DEFAULT_TEXTURES[layer];
        }

        return textureCache.computeIfAbsent(breedId, DragonRenderer::computeTextureCacheFor)[layer];
    }

    @Override
    protected void setupRotations(DragonEntity dragon, PoseStack ps, float age, float yaw, float partials, float scale) {
        super.setupRotations(dragon, ps, age, yaw, partials, scale);
        // FIX: "cannot find symbol: method getModelOffsetX() location:
        // variable animator of type Object" - real crash log error.
        // getAnimator() returns Object now (see DragonEntity's own doc
        // on the split-source-set workaround) - explicit cast needed
        // here, safe to do since THIS file is client-only already, so
        // referencing DragonAnimator directly is fine.
        DragonAnimator animator = (DragonAnimator) dragon.getAnimator();
        super.scale(dragon, ps, partials);
        scale = dragon.getAgeScale();
        ps.scale(scale, scale, scale);
        ps.translate(animator.getModelOffsetX(), animator.getModelOffsetY(), animator.getModelOffsetZ());
        ps.translate(0, 1.5, 0.5);
        ps.mulPose(Axis.XP.rotationDegrees(animator.getModelPitch(partials)));
        // "I do want them leaning while turning like saints" per
        // explicit direction - the whole-model roll, driven by
        // DragonAnimator's own new getBankAngle() (see that file's own
        // doc - ported directly from Saints Dragons' real
        // tickBanking(), a separate mechanic from barrel-roll entirely).
        // FIX: "when leaning left or right when turning in flight, the
        // dragon leans the opposite direction" per explicit direction -
        // ported Saints' own bank-angle formula directly, but their
        // sign convention doesn't automatically carry over to this
        // project's own model/coordinate setup. Simple sign flip.
        ps.mulPose(Axis.ZP.rotationDegrees(-animator.getBankAngle(partials)));
        ps.translate(0, -1.5, -0.5);
    }

    // dragons dissolve during death, not flip.
    @Override
    protected float getFlipDegrees(DragonEntity pLivingEntity) {
        return 0;
    }

    private static ResourceLocation[] computeTextureCacheFor(ResourceLocation breedId) {
        final String[] TEXTURES = {"body", "glow", "saddle"}; // 0, 1, 2

        ResourceLocation[] cache = new ResourceLocation[TEXTURES.length];
        for (int i = 0; i < TEXTURES.length; i++) {
            cache[i] = ResourceLocation.tryBuild(breedId.getNamespace(), "textures/entity/dragon/" + breedId.getPath() + "/" + TEXTURES[i] + ".png");
        }
        return cache;
    }

    public final RenderLayer<DragonEntity, DragonModel> GLOW_LAYER = new RenderLayer<>(this) {
        @Override
        public void render(PoseStack pMatrixStack, MultiBufferSource buffer, int pPackedLight, DragonEntity dragon, float pLimbSwing, float pLimbSwingAmount, float pPartialTicks, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
            if (dragon.deathTime == 0) {
                var type = CustomRenderTypes.glow(getTextureForLayer(dragon.getBreedId(), LAYER_GLOW));
                getParentModel().renderToBuffer(pMatrixStack, buffer.getBuffer(type), 0xffffff, OverlayTexture.NO_OVERLAY, -1);
            }
        }
    };
    public final RenderLayer<DragonEntity, DragonModel> SADDLE_LAYER = new RenderLayer<>(this) {
        @Override
        public void render(PoseStack ps, MultiBufferSource buffer, int light, DragonEntity dragon, float pLimbSwing, float pLimbSwingAmount, float pPartialTicks, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
            if (dragon.isSaddled()) {
                renderColoredCutoutModel(getParentModel(), getTextureForLayer(dragon.getBreedId(), LAYER_SADDLE), ps, buffer, light, dragon, -1);
            }
        }
    };
    public final RenderLayer<DragonEntity, DragonModel> DEATH_LAYER = new RenderLayer<>(this) {
        @Override
        public void render(PoseStack ps, MultiBufferSource buffer, int light, DragonEntity dragon, float limbSwing, float limbSwingAmount, float partials, float age, float yaw, float pitch) {
            if (dragon.deathTime > 0) {
                int delta = (int) (255 * (dragon.deathTime / (float) dragon.getMaxDeathTime()));
                getParentModel().renderToBuffer(ps, buffer.getBuffer(CustomRenderTypes.DISSOLVE), light, OverlayTexture.NO_OVERLAY, 0xf | (delta << 24));
                getParentModel().renderToBuffer(ps, buffer.getBuffer(RenderType.entityDecal(getTextureLocation(dragon))), light, OverlayTexture.pack(0, true), -1);
            }
        }
    };
    /**
     * "Do the wingtip/tail speed particles while diving... make sure
     * the particles you use are the same as saints" per explicit
     * direction - adapted from their own real DragonDiveTrailRenderer
     * (see that file's own doc for the honest trade-off: entity-
     * relative approximate tip positions rather than true per-bone
     * tracking).
     */
    public final RenderLayer<DragonEntity, DragonModel> DIVE_TRAIL_LAYER = new RenderLayer<>(this) {
        @Override
        public void render(PoseStack ps, MultiBufferSource buffer, int light, DragonEntity dragon, float limbSwing, float limbSwingAmount, float partials, float age, float yaw, float pitch) {
            if (dragon.deathTime == 0) {
                com.dragonspeech.client.dragon.vfx.DragonDiveTrailRenderer.render(dragon, buffer, ps.last(), partials);
            }
        }
    };

    private static class CustomRenderTypes extends RenderType {
        private static final RenderType DISSOLVE = RenderType.dragonExplosionAlpha(DISSOLVE_TEXTURE);
        private static final Function<ResourceLocation, RenderType> GLOW_FUNC = Util.memoize(texture -> create("eyes", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                .setShaderState(RENDERTYPE_EYES_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false)));

        private static RenderType glow(ResourceLocation texture) {
            return GLOW_FUNC.apply(texture);
        }

        @SuppressWarnings("DataFlowIssue")
        private CustomRenderTypes() {
            super(null, null, null, 0, false, true, null, null);
        }
    }
}
