package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.block.BlockType;
import com.dragonspeech.engine.Element;
import com.dragonspeech.engine.MagicAffinity;
import com.dragonspeech.engine.Strikes;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import com.dragonspeech.word.Word;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Backs the Earth domain's pillar ladder (sula / sulbinda). One word,
 * two things happening at once - because in practice they're the same
 * thing: stacking real blocks beneath the caster's feet as they rise
 * IS the "launch yourself into the air on the blocks below you" ask.
 * There's no separate "launch" mechanic bolted on; the caster's
 * position is simply moved to stand on top of whatever got built.
 *
 * Same named-material discipline as ShapeBlockEffectHandler - a pillar
 * must say what it's made of (jord, steinn, vidr, sandr, eldsteinn,
 * endasteinn).
 *
 * Safety: stops growing the instant it hits a non-air/non-replaceable
 * block (so this can't punch through a ceiling or an existing
 * structure - it just makes a shorter pillar and stops there, which
 * doubles as the only "don't launch me into a ceiling" check this needs).
 */
public class PillarEffectHandler implements EffectHandler {

    private static final int MAX_HEIGHT = 8;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, MAX_HEIGHT, 1f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("pillar");
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
        return 6f + Math.max(0f, invocation.modifierMagnitudeSum()) * 3f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<BlockType> named = namedBlock(invocation);
        MagicAffinity affinity = MagicAffinity.resolve(invocation.composition().words());
        Optional<BlockState> spokenMaterial = resolveMaterial(invocation, named, affinity);
        if (spokenMaterial.isEmpty()) {
            return EffectResult.failure(
                "The word reaches for matter and finds none named - a pillar must name what it is made of.");
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        int requestedHeight = Math.round(3f + Math.max(0f, invocation.modifierMagnitudeSum()) * 3f);
        int height = Math.max(1, Math.min(requestedHeight, MAX_HEIGHT));
        BlockState material = spokenMaterial.get();

        // `voddr` supplies the missing piercing shape without inventing an "ice spike ability".
        // `sulbinda is voddr` therefore means "raise bound ice as a long piercing point" and
        // grows at the place under the caster's gaze instead of underneath the caster.
        if (hasWord(invocation, "voddr")) {
            return raiseSpike(invocation, caster, level, material, affinity, height);
        }

        BlockPos feet = caster.blockPosition();

        int built = 0;
        for (int i = 0; i < height; i++) {
            BlockPos pos = feet.above(i);
            BlockState current = level.getBlockState(pos);
            if (!current.canBeReplaced() && !current.isAir()) {
                break; // hit something solid - stop the pillar here rather than push through it
            }
            level.setBlockAndUpdate(pos, material);
            built++;
        }

        if (built == 0) {
            return EffectResult.failure("There is no room here for stone to rise.");
        }

        // Carry the caster up on top of what was just built. setPos (not
        // teleportTo) to match TeleportEffectHandler's existing, lower-risk
        // pattern in this codebase rather than guessing at a different
        // overload here.
        caster.setPos(caster.getX(), feet.getY() + built, caster.getZ());
        // VERSION-RISK NOTE: Entity.fallDistance is a long-standing public
        // field used to zero out accumulated fall damage - without this,
        // riding a pillar up and then stepping off would apply fall damage
        // as if the caster had fallen from that height. If this field has
        // moved/renamed in your build, check Entity's decompiled source.
        caster.fallDistance = 0f;

        Vec3 base = Vec3.atBottomCenterOf(feet);
        SpellBodyVfx.emit(level, caster, SpellBodyVfxType.PILLAR_RISE, materialElements(named, affinity),
            base, base.add(0, built, 0), .46f, built, 12);

        return EffectResult.success(built, "The ground answers and visibly rises, and you rise with it.");
    }

    private static List<Element> materialElements(Optional<BlockType> type, MagicAffinity affinity) {
        if (affinity.element().isPresent()) return List.of(affinity.element().get());
        if (type.isPresent()) {
            return switch (type.get()) {
                case WOOD -> List.of(Element.LIFE, Element.EARTH);
                case NETHERRACK -> List.of(Element.FIRE, Element.EARTH);
                default -> List.of(Element.EARTH);
            };
        }
        return List.of(Element.EARTH);
    }

    private static Optional<BlockState> resolveMaterial(EffectInvocation invocation, Optional<BlockType> named, MagicAffinity affinity) {
        if (named.isPresent()) return Optional.of(named.get().blockState());
        return switch (affinity) {
            case ICE -> Optional.of(hasWord(invocation, "mikla") ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
            case EARTH -> Optional.of(Blocks.STONE.defaultBlockState());
            default -> Optional.empty();
        };
    }

    private static EffectResult raiseSpike(EffectInvocation invocation, ServerPlayer caster, ServerLevel level,
                                           BlockState material, MagicAffinity affinity, int height) {
        Vec3 eye = caster.getEyePosition();
        Strikes.StrikeHit hit = Strikes.ray(caster, eye, caster.getLookAngle(), 24.0);
        Vec3 aim = hit.pos() != null ? hit.pos() : eye.add(caster.getLookAngle().scale(12));
        Vec3 ground = Strikes.dropToGround(level, caster, aim.add(0, 2, 0), 20);
        BlockPos basePos = BlockPos.containing(ground);

        int built = 0;
        for (int i = 0; i < height; i++) {
            BlockPos pos = basePos.above(i);
            BlockState current = level.getBlockState(pos);
            if (!current.canBeReplaced() && !current.isAir()) break;
            level.setBlockAndUpdate(pos, material);
            built++;
        }
        if (built == 0) return EffectResult.failure("The point finds no room to rise.");

        Vec3 base = Vec3.atBottomCenterOf(basePos);
        Vec3 tip = base.add(0, built, 0);
        List<Element> elements = materialElements(namedBlock(invocation), affinity);
        SpellBodyVfx.emit(level, caster, SpellBodyVfxType.LANCE, elements, base, tip, .42f, Math.max(1f, built * .7f), 14);

        float impact = Math.min(12f, 3.5f + built + Math.max(0f, invocation.modifierMagnitudeSum()) * 2f);
        AABB hitBox = new AABB(basePos).inflate(.65, built + .5, .65);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, hitBox, e -> e != caster && e.isAlive())) {
            if (affinity.element().isPresent()) affinity.element().get().hitEntity(caster, living, impact);
            else living.hurt(level.damageSources().indirectMagic(caster, caster), impact);
            living.push(0, .35 + built * .04, 0);
            living.hurtMarked = true;
        }
        return EffectResult.success(built, affinity == MagicAffinity.ICE
            ? "Ice answers the pillar-word as a piercing spike beneath your mark."
            : "Bound matter spears upward beneath your mark.");
    }

    private static boolean hasWord(EffectInvocation invocation, String name) {
        return invocation.composition().words().stream().anyMatch(w -> name.equals(w.trueName()));
    }

    private static Optional<BlockType> namedBlock(EffectInvocation invocation) {
        return invocation.composition().words().stream()
            .map(Word::blockType)
            .flatMap(Optional::stream)
            .findFirst();
    }
}
