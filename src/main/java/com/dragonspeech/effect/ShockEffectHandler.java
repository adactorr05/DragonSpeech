package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Backs the Air domain's lightning ladder (glitra / eldingkast /
 * thrumubinda). Unlike Ignite/Freeze/Confuse, this doesn't hand-roll
 * damage or a MobEffect - it spawns a real vanilla LightningBolt at each
 * resolved target, which already carries its own damage, particles, and
 * sound. This keeps the handler honest: "lightning" behaves exactly like
 * vanilla lightning everywhere else in the game, rather than an
 * approximation of it.
 *
 * VERSION-RISK NOTE: EntityType.LIGHTNING_BOLT / LightningBolt.setCause /
 * Entity.setPos are long-standing Mojang-mapped names, same caveat as
 * every other handler in this project - no compiler is available in this
 * environment to verify against your exact 1.21.1 build.
 */
public class ShockEffectHandler implements EffectHandler {

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        3, 1.0f, 32f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("shock");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        // Lightning is a big, singular event rather than a scalable
        // duration/severity effect, so cost scales with target COUNT only -
        // "how many bolts," not "how strong is this one bolt."
        return 6.0f * Math.max(invocation.targets().size(), 1);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel serverLevel)) {
            return EffectResult.failure("The sky cannot answer here.");
        }

        int affected = 0;
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity)) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
                if (bolt == null) {
                    continue;
                }
                Vec3 pos = entity.position();
                bolt.setPos(pos.x, pos.y, pos.z);
                bolt.setCause(caster);
                serverLevel.addFreshEntity(bolt);
                affected++;
            }
        }

        return EffectResult.success(affected, "The word cracks like thunder, and the sky answers.");
    }
}
