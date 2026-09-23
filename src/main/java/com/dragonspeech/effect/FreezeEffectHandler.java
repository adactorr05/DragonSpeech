package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.engine.Element;
import com.dragonspeech.fx.SpellBodyVfx;
import com.dragonspeech.fx.SpellBodyVfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Backs the Water domain's cold ladder (kaldna / frysta): slows living
 * targets heavily, extinguishes burning ones, and freezes water source
 * blocks to ice. The counterpart to Ignite.
 */
public class FreezeEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
            6, 6.0f, 24f, Set.of(TargetKind.ENTITY, TargetKind.BLOCK)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("freeze");
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

        float severitySeconds = 3f + (invocation.modifierMagnitudeSum() * 3f);
        severitySeconds = Math.max(1f, Math.min(severitySeconds, CAPS.maxMagnitudePerTarget()));
        int ticks = Math.round(severitySeconds * 20f);

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            switch (target) {
                case EffectTarget.OfEntity(Entity entity) -> {
                    if (entity instanceof LivingEntity living && com.dragonspeech.ward.WardInterception.blocksElement(living, Element.ICE, Math.max(1f, severitySeconds))) continue;
                    if (entity instanceof LivingEntity living) {
                        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3));
                        living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 1));
                    }

                    entity.clearFire();
                    if (entity.level() instanceof ServerLevel serverLevel) {
                        Vec3 focus = entity.position().add(0, entity.getBbHeight() * .50, 0);
                        SpellBodyVfx.emit(serverLevel, null, SpellBodyVfxType.CONVERGENCE, List.of(Element.ICE),
                            focus, focus, .58f + severitySeconds * .04f, 0f, 10);
                        SpellBodyVfx.emit(serverLevel, null, SpellBodyVfxType.SHELL, List.of(Element.ICE),
                            focus, focus, (float)Math.max(.55, entity.getBbWidth() * .72 + .22),
                            (float)Math.max(.72, entity.getBbHeight() * .47), 13);
                    }
                    affected++;
                }

                case EffectTarget.OfBlock(BlockPos pos) -> {
                    Level level = invocation.caster().level();

                    if (level.getBlockState(pos).is(Blocks.WATER)) {
                        level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                        if (level instanceof ServerLevel serverLevel) {
                            Vec3 focus = Vec3.atCenterOf(pos);
                            SpellBodyVfx.emit(serverLevel, null, SpellBodyVfxType.CONVERGENCE, List.of(Element.ICE),
                                focus, focus, .64f, 0f, 10);
                            SpellBodyVfx.emit(serverLevel, null, SpellBodyVfxType.IMPACT, List.of(Element.ICE),
                                focus, focus, .48f, 0f, 8);
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

        return EffectResult.success(affected, "Cold answers the word, and the world stills.");
    }
}