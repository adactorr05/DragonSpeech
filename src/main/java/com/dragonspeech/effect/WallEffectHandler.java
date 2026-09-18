package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.block.BlockType;
import com.dragonspeech.engine.Element;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import com.dragonspeech.word.Word;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Backs the Earth domain's wall ladder (veggja / veggbinda). A physical
 * barrier of real blocks, distinct from "verja" (a magical ward) - this
 * is Sunder's/Shape's world, not the Binding domain's. Same named-
 * material requirement as Pillar/Shape.
 *
 * The wall is fixed at 3 wide x 3 tall (9 blocks, hard-capped) centered
 * on the caster's horizontal facing, built 2 blocks ahead of them so it
 * doesn't overlap the caster's own space. Same "only replace air/
 * replaceable blocks" safety rule as Pillar - it will not overwrite
 * existing structures, it just leaves gaps where something solid
 * already stood.
 */
public class WallEffectHandler implements EffectHandler {

    private static final int WIDTH = 3;
    private static final int HEIGHT = 3;
    private static final int DISTANCE_AHEAD = 2;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, WIDTH * HEIGHT, 6f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("wall");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public boolean selfTargeting() {
        return true;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 7f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<BlockType> named = namedBlock(invocation);
        if (named.isEmpty()) {
            return EffectResult.failure(
                "The word reaches for matter and finds none named - a wall must name what it is made of.");
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        Vec3 look = caster.getLookAngle();
        Vec3 forwardFlat = new Vec3(look.x, 0, look.z);
        forwardFlat = forwardFlat.lengthSqr() < 0.0001 ? new Vec3(0, 0, 1) : forwardFlat.normalize();
        // Perpendicular (right-hand) vector in the horizontal plane, used to lay out the wall's width.
        Vec3 side = new Vec3(-forwardFlat.z, 0, forwardFlat.x);

        BlockPos center = caster.blockPosition().offset(
            (int) Math.round(forwardFlat.x * DISTANCE_AHEAD), 0, (int) Math.round(forwardFlat.z * DISTANCE_AHEAD));

        BlockState material = named.get().blockState();
        int built = 0;
        int half = WIDTH / 2;

        for (int w = -half; w <= half; w++) {
            BlockPos column = center.offset((int) Math.round(side.x * w), 0, (int) Math.round(side.z * w));
            for (int h = 0; h < HEIGHT; h++) {
                BlockPos pos = column.above(h);
                BlockState current = level.getBlockState(pos);
                if (!current.canBeReplaced() && !current.isAir()) {
                    continue; // leave existing structure alone, just skip this cell
                }
                level.setBlockAndUpdate(pos, material);
                built++;
            }
        }

        if (built == 0) {
            return EffectResult.failure("There is no room here for a wall to stand.");
        }

        // Dragon Flux-style physical construction cue: the blocks remain server-authoritative,
        // while a geometry body visibly rises through them instead of nine blocks simply popping in.
        Vec3 base = Vec3.atBottomCenterOf(center);
        Vec3 edgeA = base.add(side.scale(-half));
        Vec3 edgeB = base.add(side.scale(half));
        SpellBodyVfx.emit(level, caster, SpellBodyVfxType.WALL_RISE, materialElements(named.get()),
            edgeA, edgeB, .55f, HEIGHT, 12);

        return EffectResult.success(built, "Matter answers, and a barrier rises where none stood.");
    }

    private static List<Element> materialElements(BlockType type) {
        return switch (type) {
            case WOOD -> List.of(Element.LIFE, Element.EARTH);
            case NETHERRACK -> List.of(Element.FIRE, Element.EARTH);
            default -> List.of(Element.EARTH);
        };
    }

    private static Optional<BlockType> namedBlock(EffectInvocation invocation) {
        return invocation.composition().words().stream()
            .map(Word::blockType)
            .flatMap(Optional::stream)
            .findFirst();
    }
}
