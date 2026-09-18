package com.dragonspeech.client.renderer;

import com.dragonspeech.client.model.HumanMageEntityModel;
import com.dragonspeech.human.HumanMageEntity;
import com.dragonspeech.DragonSpeech;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders HumanMageEntity with HumanMageEntityModel and the texture at
 * assets/dragonspeech/textures/entity/human_mage/human_mage.png - see that
 * file's own note (or the mod's textures folder) if it's still the
 * placeholder flat color this groundwork pass shipped with; swap in real
 * art whenever it's ready, no code changes needed here.
 */
public class HumanMageRenderer extends MobRenderer<HumanMageEntity, HumanMageEntityModel<HumanMageEntity>> {

	private static final ResourceLocation TEXTURE = DragonSpeech.id("textures/entity/human/human_mage.png");

	public HumanMageRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanMageEntityModel<>(context.bakeLayer(HumanMageEntityModel.LAYER_LOCATION)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(HumanMageEntity entity) {
		return TEXTURE;
	}
}
