package com.dragonspeech.client.renderer;

import com.dragonspeech.client.model.ElderElfEntityModel;
import com.dragonspeech.elf.ElderElfEntity;
import com.dragonspeech.DragonSpeech;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders ElderElfEntity with ElderElfEntityModel and the texture at
 * assets/dragonspeech/textures/entity/elder_elf/elder_elf.png - see that
 * file's own note (or the mod's textures folder) if it's still the
 * placeholder flat color this groundwork pass shipped with; swap in real
 * art whenever it's ready, no code changes needed here.
 */
public class ElderElfRenderer extends MobRenderer<ElderElfEntity, ElderElfEntityModel<ElderElfEntity>> {

	private static final ResourceLocation TEXTURE = DragonSpeech.id("textures/entity/elf/elder_elf.png");

	public ElderElfRenderer(EntityRendererProvider.Context context) {
		super(context, new ElderElfEntityModel<>(context.bakeLayer(ElderElfEntityModel.LAYER_LOCATION)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(ElderElfEntity entity) {
		return TEXTURE;
	}
}
