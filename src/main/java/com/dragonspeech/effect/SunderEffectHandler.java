package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;

/**
 * Backs "brjota" - breaks a targeted block, dropping it as if mined.
 *
 * CAPPED HARD ON PURPOSE: only blocks up to a fixed hardness break
 * (stone yes, obsidian no), unbreakable blocks (bedrock etc., hardness
 * < 0) never break, and only ONE block per cast. Turning this into
 * area terraforming is exactly the kind of scaling that must come from
 * a deliberately-designed higher-tier handler someday, not from raising
 * these numbers.
 */
public class SunderEffectHandler implements EffectHandler {

    private static final float MAX_HARDNESS = 5.0f; // stone=1.5, ores~3, obsidian=50 (excluded)

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, MAX_HARDNESS, 16f, Set.of(TargetKind.BLOCK)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("sunder");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        // Cost scales with the actual block's hardness - sundering deepslate
        // genuinely costs more than sundering dirt.
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfBlock(BlockPos pos)) {
                float hardness = invocation.caster().level().getBlockState(pos)
                    .getDestroySpeed(invocation.caster().level(), pos);
                return 4f + Math.max(0f, hardness) * 3f;
            }
        }
        return 6f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfBlock(BlockPos pos)
                && invocation.caster().level() instanceof ServerLevel level) {

                BlockState blockState = level.getBlockState(pos);
                float hardness = blockState.getDestroySpeed(level, pos);

                if (hardness < 0f || hardness > MAX_HARDNESS) {
                    return EffectResult.failure("The stone resists - it is beyond this word's strength.");
                }

                level.destroyBlock(pos, true, invocation.caster());
                return EffectResult.success(1, "The word finds the seams, and the stone gives way.");
            }
        }
        return EffectResult.failure("There is nothing there to sunder.");
    }
}
