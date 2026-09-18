package com.dragonspeech.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Additive untextured geometry for the visible body of composed Dragon Speech workings. */
public final class SpellBodyVfxRenderTypes {
    public static final RenderType MAGIC = RenderType.create(
        "dragonspeech_spell_body",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        131072,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(false)
    );

    private SpellBodyVfxRenderTypes() {}
}
