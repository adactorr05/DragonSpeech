package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.block.BlockType;
import com.dragonspeech.word.Word;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.Set;

/**
 * Backs the Earth domain's shaping ladder (gera / skapbinda) - the
 * constructive counterpart to "brjota" (SunderEffectHandler). Where
 * Sunder needs no noun (it just breaks whatever you're looking at),
 * Shape needs one: exactly the "wounds have names" discipline
 * HealEffectHandler enforces for healing, and SummonEffectHandler
 * enforces for creatures, applied here to materials - an unworded
 * "gera" alone has nothing to pour shape into and fails outright.
 *
 * Same safety cap philosophy as Sunder: only overwrites blocks up to a
 * fixed hardness (so you can't, say, transmute bedrock), and only ONE
 * block per cast. This is a material swap, not terraforming.
 */
public class ShapeBlockEffectHandler implements EffectHandler {

    private static final float MAX_HARDNESS = 5.0f; // matches SunderEffectHandler's cap

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, MAX_HARDNESS, 16f, Set.of(TargetKind.BLOCK)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("shape_block");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 5f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<BlockType> named = namedBlock(invocation);
        if (named.isEmpty()) {
            return EffectResult.failure(
                "The word shapes, and nothing takes form - a shaping must name the material it wants (jord, steinn, vidr, sandr, eldsteinn, endasteinn).");
        }

        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        if (!(invocation.caster().level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfBlock(BlockPos pos)) {
                BlockState current = level.getBlockState(pos);
                float hardness = current.getDestroySpeed(level, pos);

                if (hardness < 0f || hardness > MAX_HARDNESS) {
                    return EffectResult.failure("The stone resists - it is beyond this word's strength to reshape.");
                }

                level.setBlockAndUpdate(pos, named.get().blockState());
                return EffectResult.success(1, "The word finds the shape it was given, and matter answers.");
            }
        }
        return EffectResult.failure("There is nothing there to shape.");
    }

    /** The first noun in the sentence that names a block material. */
    private static Optional<BlockType> namedBlock(EffectInvocation invocation) {
        return invocation.composition().words().stream()
            .map(Word::blockType)
            .flatMap(Optional::stream)
            .findFirst();
    }
}
