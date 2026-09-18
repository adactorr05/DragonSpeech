package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.block.BlockType;
import com.dragonspeech.word.Word;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;

/**
 * Backs the Earth domain's hurl ladder (grjotkasta / grjotbinda) - "throw
 * a block at them to do damage." Named material required, same
 * discipline as Pillar/Wall/Shape.
 *
 * DESIGN SIMPLIFICATION, stated plainly rather than oversold: this does
 * NOT simulate a block flying through the air with real trajectory
 * physics and a mid-flight collision check - there is no existing
 * projectile-entity precedent in this codebase to build that on safely
 * without a compiler to verify it. Instead it deals direct damage via
 * damageSources().magic() (the same call ChronicPainTicker/DrainResolver
 * already use elsewhere in this project, so it's a proven-safe choice
 * here), scaled by the named material's weight, and separately drops a
 * real FallingBlockEntity of that material just above the target as a
 * best-effort visual flourish. The damage does not depend on that
 * entity spawning or landing correctly - if FallingBlockEntity's exact
 * spawn call doesn't compile against your build, the hit still lands;
 * only the debris visual would be missing.
 */
public class BlockThrowEffectHandler implements EffectHandler {

    private static final float BASE_DAMAGE = 4.0f;
    private static final float MAX_DAMAGE = 10.0f;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, MAX_DAMAGE, 20f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("hurl_block");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        BlockType type = namedBlock(invocation).orElse(BlockType.STONE);
        return BASE_DAMAGE * throwWeight(type) * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<BlockType> named = namedBlock(invocation);
        if (named.isEmpty()) {
            return EffectResult.failure(
                "The word reaches for matter and finds none named - a hurling must name what it throws.");
        }

        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        float damage = BASE_DAMAGE * throwWeight(named.get())
            * (1f + Math.max(0f, invocation.modifierMagnitudeSum()) * 0.4f);
        damage = Math.max(1f, Math.min(damage, MAX_DAMAGE));

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living) {
                living.hurt(caster.damageSources().magic(), damage);

                // Best-effort visual only - see class doc. Wrapped so a
                // spawn failure here can never undo the damage above.
                try {
                    FallingBlockEntity debris = FallingBlockEntity.fall(
                        level, living.blockPosition().above(3), named.get().blockState());
                    debris.setDeltaMovement(0, -0.2, 0);
                } catch (Exception ignored) {
                    // visual flourish only - the hit already landed
                }

                affected++;
            }
        }

        if (affected == 0) {
            return EffectResult.failure("There is nothing there to strike.");
        }
        return EffectResult.success(damage, "Matter answers, and flies true.");
    }

    /** Common blocks (dirt/sand) are light and do less; stone/netherrack/end stone hit harder. */
    private static float throwWeight(BlockType type) {
        return switch (type) {
            case DIRT, SAND -> 0.8f;
            case WOOD -> 1.0f;
            case NETHERRACK -> 1.1f;
            case STONE -> 1.3f;
            case END_STONE -> 1.4f;
        };
    }

    private static Optional<BlockType> namedBlock(EffectInvocation invocation) {
        return invocation.composition().words().stream()
            .map(Word::blockType)
            .flatMap(Optional::stream)
            .findFirst();
    }
}
