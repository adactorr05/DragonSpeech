package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.wound.PlayerWounds;
import com.dragonspeech.wound.WoundAccess;
import com.dragonspeech.wound.WoundType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;

/**
 * Mending flesh is the costliest common working in the language, and it
 * follows two rules straight from the design:
 *
 * WOUNDS HAVE NAMES. A player's damage is remembered by KIND (see
 * WoundType / the damage mixin), and a healing sentence must name the
 * wound it mends with the matching noun: "graeda bein" knits bone,
 * "graedbinda eldr sjalfan" closes your own burns. A plain unworded
 * heal can only touch GENERIC hurt (drowning, magic, hunger's toll).
 * Naming the wrong wound mends nothing and still costs the attempt.
 * Non-player targets carry no wound ledger - they take plain (pricier)
 * mending.
 *
 * DESPERATION PRICES IN. The nearer a body is to death, the more it
 * costs to pull it back - grave wounds are grave work. Healing early
 * and often is cheap; healing at half a heart is a sacrifice.
 */
public class HealEffectHandler implements EffectHandler {

    /** Raw energy per point of health mended, before desperation and the sentence's own multipliers. Steep on purpose. */
    private static final float ENERGY_PER_HEALTH = 5.0f;

    /** At the edge of death the same wound costs this many times more to mend. */
    private static final float DESPERATION_MAX = 3.0f;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, 10.0f, 24f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("heal");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        for (EffectTarget target : invocation.targets()) {
            if (target instanceof EffectTarget.OfEntity(Entity entity) && entity instanceof LivingEntity living) {
                float intended = intendedHealAmount(invocation);
                return ENERGY_PER_HEALTH * intended * desperation(living);
            }
        }
        return ENERGY_PER_HEALTH * 4f;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<String> capViolation = CAPS.validateTargets(invocation);
        if (capViolation.isPresent()) {
            return EffectResult.failure(capViolation.get());
        }

        for (EffectTarget target : invocation.targets()) {
            if (!(target instanceof EffectTarget.OfEntity(Entity entity)) || !(entity instanceof LivingEntity living)) {
                continue;
            }

            float missing = living.getMaxHealth() - living.getHealth();
            if (missing <= 0.01f) {
                return EffectResult.failure("There is nothing there that needs mending.");
            }

            float intended = Math.min(intendedHealAmount(invocation), missing);
            Optional<WoundType> named = namedWound(invocation);

            if (living instanceof ServerPlayer patient) {
                return healPlayer(patient, named, intended);
            }

            // Mobs carry no wound ledger - plain mending works on them.
            living.heal(intended);
            return EffectResult.success(intended, "The flesh answers, and knits.");
        }
        return EffectResult.failure("There is nothing there to mend.");
    }

    private static EffectResult healPlayer(ServerPlayer patient, Optional<WoundType> named, float intended) {
        PlayerWounds wounds = WoundAccess.get(patient);
        WoundType pool = named.orElse(WoundType.GENERIC);

        // The wound ledger can drift above real missing health (natural
        // regen mends flesh without clearing the ledger) - clamp on read.
        float missing = patient.getMaxHealth() - patient.getHealth();
        float available = Math.min(wounds.get(pool), missing);

        // Unworded healing may also touch hurt that has no ledger entry at
        // all (old wounds naturally regenerated then re-lost, hunger damage
        // before tracking, etc.) - the untyped remainder counts as GENERIC.
        if (pool == WoundType.GENERIC) {
            float ledgerTotal = 0f;
            for (WoundType type : WoundType.values()) {
                ledgerTotal += wounds.get(type);
            }
            float untracked = Math.max(0f, missing - ledgerTotal);
            available = Math.min(wounds.get(WoundType.GENERIC) + untracked, missing);
        }

        if (available <= 0.01f) {
            return EffectResult.failure(named.isPresent()
                ? "The body carries no such wound - the word finds nothing to mend."
                : "This hurt has a name, and the word will not mend what it does not name.");
        }

        float healed = Math.min(intended, available);
        patient.heal(healed);
        WoundAccess.set(patient, wounds.withReduced(pool, healed));

        return EffectResult.success(healed, named.isPresent()
            ? "The named wound closes, flesh remembering its shape."
            : "The unnamed hurt eases.");
    }

    /** How much the sentence is TRYING to mend - modifiers push it, caps clamp it. */
    private static float intendedHealAmount(EffectInvocation invocation) {
        float amount = 6f + (invocation.modifierMagnitudeSum() * 4f);
        return Math.max(1f, Math.min(amount, CAPS.maxMagnitudePerTarget()));
    }

    /** 1.0 at full health up to DESPERATION_MAX at the edge of death - grave wounds are grave work. */
    private static float desperation(LivingEntity living) {
        float fraction = living.getMaxHealth() > 0 ? living.getHealth() / living.getMaxHealth() : 1f;
        return 1.0f + (1.0f - Math.max(0f, Math.min(1f, fraction))) * (DESPERATION_MAX - 1.0f);
    }

    /** The first noun in the sentence that names a wound kind. */
    private static Optional<WoundType> namedWound(EffectInvocation invocation) {
        return invocation.composition().words().stream()
            .map(com.dragonspeech.word.Word::woundType)
            .flatMap(Optional::stream)
            .findFirst();
    }
}
