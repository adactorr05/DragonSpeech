package com.dragonspeech.client.dragon;

import com.dragonspeech.eldunari.DragonHeartVesselEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Renamed from EldunariVesselRenderer - see DragonHeartState's own doc
 * for the rename's reasoning. Same pattern and same reasoning as
 * com.dragonspeech.client.entity.MagicBarrierRenderer:
 * DragonHeartVesselEntity is server-side bookkeeping (see its javadoc)
 * that happens to need a real EntityType/renderer registered, but is
 * already setInvisible(true) server-side and should never actually be
 * seen.
 */
public class DragonHeartVesselRenderer extends EntityRenderer<DragonHeartVesselEntity> {

    public DragonHeartVesselRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DragonHeartVesselEntity entity) {
        return MissingTextureAtlasSprite.getLocation();
    }

    @Override
    public void render(DragonHeartVesselEntity entity, float entityYaw, float partialTicks,
                        PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Intentionally empty - see class comment.
    }
}
