package com.dragonspeech.client.renderer;

import com.dragonspeech.client.model.ShadeEntityModel;
import com.dragonspeech.shade.ShadeEntity;
import com.dragonspeech.DragonSpeech;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders ShadeEntity with ShadeEntityModel and the texture at
 * assets/dragonspeech/textures/entity/shade/shade.png - see that
 * file's own note (or the mod's textures folder) if it's still the
 * placeholder flat color this groundwork pass shipped with; swap in real
 * art whenever it's ready, no code changes needed here.
 */
public class ShadeRenderer extends MobRenderer<ShadeEntity, ShadeEntityModel<ShadeEntity>> {

	private static final ResourceLocation TEXTURE = DragonSpeech.id("textures/entity/shade/shade.png");

	public ShadeRenderer(EntityRendererProvider.Context context) {
		super(context, new ShadeEntityModel<>(context.bakeLayer(ShadeEntityModel.LAYER_LOCATION)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(ShadeEntity entity) {
		return TEXTURE;
	}
}
