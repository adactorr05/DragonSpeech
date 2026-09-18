package com.dragonspeech.client.renderer;

import com.dragonspeech.client.model.ElfEntityModel;
import com.dragonspeech.elf.ElfEntity;
import com.dragonspeech.DragonSpeech;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders ElfEntity with ElfEntityModel and the texture at
 * assets/dragonspeech/textures/entity/elf/elf.png - see that
 * file's own note (or the mod's textures folder) if it's still the
 * placeholder flat color this groundwork pass shipped with; swap in real
 * art whenever it's ready, no code changes needed here.
 */
public class ElfRenderer extends MobRenderer<ElfEntity, ElfEntityModel<ElfEntity>> {

	private static final ResourceLocation TEXTURE = DragonSpeech.id("textures/entity/elf/elf.png");

	public ElfRenderer(EntityRendererProvider.Context context) {
		super(context, new ElfEntityModel<>(context.bakeLayer(ElfEntityModel.LAYER_LOCATION)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(ElfEntity entity) {
		return TEXTURE;
	}
}
