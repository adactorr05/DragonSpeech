package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.Element;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Sets entities alight or ignites air blocks. Backs the Fire domain's
 * ignite ladder (vidbrenn / kyndla / brenlokk / narro).
 *
 * VERSION-RISK NOTE: entity.setRemainingFireTicks / getRemainingFireTicks
 * and Entity.level() are written against long-standing Mojang-mapped
 * method names. If these don't compile against your exact 1.21.1 build,
 * check the decompiled Entity class in your IDE for the current names -
 * this is the single most likely spot in Phase 1-2 to need a small fix.
 */
public class IgniteEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
            4,      // max targets per cast
            8.0f,   // max fire-severity per target (used below as a seconds cap)
            24f,    // max range in blocks
            Set.of(TargetKind.ENTITY, TargetKind.BLOCK)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("ignite");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 4.0f * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        // Modifier magnitude shapes duration within a hard, safety-clamped
        // range - "mikla" (greatly) burns longer, "litla" (slightly) shorter,
        // but never past CAPS.maxMagnitudePerTarget() regardless of stacking.
        float severitySeconds = 3f + (invocation.modifierMagnitudeSum() * 4f);
        severitySeconds = Math.max(1f, Math.min(severitySeconds, CAPS.maxMagnitudePerTarget()));
        int fireTicks = Math.round(severitySeconds * 20f);

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            switch (target) {
                case EffectTarget.OfEntity(Entity entity) -> {
                    entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), fireTicks));
                    if (entity.level() instanceof ServerLevel serverLevel) {
                        Vec3 focus = entity.position().add(0, entity.getBbHeight() * .52, 0);
                        SpellBodyVfx.emit(serverLevel, null, SpellBodyVfxType.CONVERGENCE, List.of(Element.FIRE),
                            focus, focus, .62f + severitySeconds * .045f, 0f, 11);
                        SpellBodyVfx.emit(serverLevel, null, SpellBodyVfxType.IMPACT, List.of(Element.FIRE),
                            focus, focus, .46f + severitySeconds * .035f, 0f, 8);
                    }
                    affected++;
                }

                case EffectTarget.OfBlock(BlockPos pos) -> {
                    Level level = invocation.caster().level();

                    if (level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
                        if (level instanceof ServerLevel serverLevel) {
                            Vec3 focus = Vec3.atCenterOf(pos);
                            SpellBodyVfx.emit(serverLevel, null, SpellBodyVfxType.CONVERGENCE, List.of(Element.FIRE),
                                focus, focus, .66f + severitySeconds * .04f, 0f, 11);
                            SpellBodyVfx.emit(serverLevel, null, SpellBodyVfxType.IMPACT, List.of(Element.FIRE),
                                focus, focus, .45f, 0f, 8);
                        }
                        affected++;
                    }
                }

                default -> {
                    // Unsupported target types are already rejected by CAPS.validateTargets(invocation).
                    // This exists so the Java switch is exhaustive.
                }
            }
        }

        return EffectResult.success(affected, "The word catches, and flame answers.");
    }
}