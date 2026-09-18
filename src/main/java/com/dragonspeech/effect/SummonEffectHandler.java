package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.mob.SummonType;
import com.dragonspeech.word.Word;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Backs the Death domain's summon ladder (kalla / kallbinda). A summon
 * sentence must name WHAT to call using one of the summon nouns
 * (beinvaettr, holdvaettr, kongurvaettr, ulfvaettr, eldvaettr) - exactly
 * the same "wounds have names" discipline HealEffectHandler enforces for
 * healing, applied here to creatures instead: an unworded "kalla" alone
 * has nothing to pour life into and fails outright, rather than falling
 * back to some default creature nobody asked for.
 *
 * QUANTITY WORDS: "kalla holdvaettr margfalt" calls several. The
 * sentence's repeat_count (margfalt = 3, tvefalt = 2 if worded so)
 * multiplies how many creatures answer, hard-capped at MAX_CALLED - and
 * since each spoken creature is priced individually, calling three costs
 * three summons' worth of stamina. The wording decides; the caps contain.
 *
 * COST, per explicit design direction: summoning should sit at HALF of
 * what ResurrectEffectHandler's base "call back a mob/player" form
 * costs. Rather than a second hardcoded number that could quietly drift
 * out of that ratio as either handler gets rebalanced later, this reads
 * ResurrectEffectHandler.BASE_MAGNITUDE_OTHER directly and halves it -
 * if resurrection's price ever changes, summon's price moves with it
 * automatically, staying at exactly half.
 *
 * Self-targeting: like ChargeItem/Teleport, this is worked by the caster
 * on the world around them, not on a look-target - the creature appears
 * a short distance in front of the caster.
 */
public class SummonEffectHandler implements EffectHandler {

    private static final float BASE_MAGNITUDE = ResurrectEffectHandler.BASE_MAGNITUDE_OTHER / 2f;

    /** However the sentence is worded, no single summoning calls more than this many. */
    private static final int MAX_CALLED = 4;

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, BASE_MAGNITUDE * SummonType.BLAZE.costMultiplier() * MAX_CALLED, 8f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("summon");
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
        float perCreature = namedSummon(invocation)
            .map(type -> BASE_MAGNITUDE * type.costMultiplier())
            .orElse(BASE_MAGNITUDE); // unworded summon still prices as an attempt, even though apply() will refuse it
        return perCreature * calledCount(invocation);
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        Optional<SummonType> named = namedSummon(invocation);
        if (named.isEmpty()) {
            return EffectResult.failure(
                "The word calls, and nothing answers - a summoning must name the shape it wants (beinvaettr, holdvaettr, ...).");
        }

        ServerPlayer caster = invocation.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return EffectResult.failure("The working slips away.");
        }

        Vec3 look = caster.getLookAngle();
        Vec3 forwardFlat = new Vec3(look.x, 0, look.z);
        forwardFlat = forwardFlat.lengthSqr() < 0.0001 ? new Vec3(0, 0, 1) : forwardFlat.normalize();
        Vec3 right = new Vec3(-forwardFlat.z, 0, forwardFlat.x);

        int toCall = calledCount(invocation);
        int called = 0;

        for (int i = 0; i < toCall; i++) {
            Entity summoned = named.get().create(level);
            if (summoned == null) {
                continue;
            }

            // The first appears ahead; extras fan out to alternating sides
            // so a manyfold calling arrives as a line, not a mob-pile.
            double sideways = (i == 0) ? 0 : (i % 2 == 1 ? 1 : -1) * (1.5 * ((i + 1) / 2));
            Vec3 spawnPos = caster.position().add(forwardFlat.scale(3.0)).add(right.scale(sideways));

            summoned.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            // finalizeSpawn is declared on Mob, not the broader LivingEntity -
            // every SummonType entry (Skeleton/Zombie/Spider/Wolf/Enderman/
            // Blaze) is a Mob, so checking for that directly is both correct
            // and gives normal spawn-time setup (equipment, attribute
            // randomization) instead of a bare, un-initialized entity.
            if (summoned instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(summoned.blockPosition()),
                    net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
            }

            level.addFreshEntity(summoned);
            called++;

            // The arrival: a swirl of dark magic and sparkles, EBW-summon style.
            Vec3 fxPos = spawnPos.add(0, summoned.getBbHeight() * 0.5, 0);
            SpellFx.burst(level, DragonSpeechParticles.DARK_MAGIC, 0x3d1a4f, 0x0e0413, fxPos, 14, 0.12);
            SpellFx.burst(level, DragonSpeechParticles.SPARKLE, 0x8a6bb8, 0x2a1a3f, fxPos, 8, 0.08);
        }

        if (called == 0) {
            return EffectResult.failure("The shape will not hold - the word finds nothing to pour life into.");
        }

        return EffectResult.success(called, called == 1
            ? "The word is spoken, and something answers the call."
            : "The word is spoken " + called + " times over, and shapes answer from the dark.");
    }

    /** The first noun in the sentence that names a summonable creature. */
    private static Optional<SummonType> namedSummon(EffectInvocation invocation) {
        return invocation.composition().words().stream()
            .map(Word::summonType)
            .flatMap(Optional::stream)
            .findFirst();
    }

    /** How many creatures the sentence calls ("margfalt" -> 3), clamped to the hard cap. */
    private static int calledCount(EffectInvocation invocation) {
        return Math.min(MAX_CALLED, invocation.composition().repeatCount());
    }
}
