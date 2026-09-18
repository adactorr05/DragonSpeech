package com.dragonspeech.stamina;

import com.dragonspeech.fx.DragonSpeechParticles;
import com.dragonspeech.fx.SpellFx;
import com.dragonspeech.spell.SpellComposition;
import com.dragonspeech.word.Word;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * "brynna" - fund WHATEVER ELSE the sentence is casting from nearby life
 * force instead of the caster's own reserves. Deliberately implemented
 * as a single modifier word usable alongside any spell ("eldingkast
 * brynna" hurls lightning paid for by draining what's nearby) rather
 * than the two-clause "aflsuga X, then feed it into Y" idea it was
 * originally described as - within this grammar, a sentence is one
 * verb plus its modifiers/targets, so a genuine two-clause "drain, THEN
 * cast" utterance would need real new sentence-composition machinery
 * (parsing and sequencing two separate clauses in one spoken phrase).
 * A modifier word gets the same practical outcome - drains fund the
 * spell instead of your stamina - without that much larger change, and
 * fits every other "word changes how THIS casting pays for itself"
 * precedent already in the mod (aflbinda for barriers is the closest
 * existing example).
 *
 * AREA VS SINGLE TARGET: reuses the scope word system that already
 * exists rather than adding two more new words for it. No area word
 * (naerum/umhverf/viddum) -> drains the SINGLE closest living thing
 * within a modest default range. WITH one -> drains everything living
 * within that scope's own reach, closest first, until the cost is
 * covered or everyone nearby runs dry. Same LifeForceDrain cascade
 * either way (stamina, then hunger for a player, then real-but-small
 * health damage) - see that class.
 *
 * Whatever this couldn't fully cover falls through to the caster's own
 * DrainResolver cascade exactly as normal - "brynna" reduces what you
 * personally pay, it doesn't guarantee zero.
 */
public final class NearbyFundingDrain {

    private static final double DEFAULT_SINGLE_TARGET_RANGE = 6.0;

    private NearbyFundingDrain() {}

    /**
     * @return how much of `requestedCost` was actually gathered from
     * nearby life force (0 if "brynna" wasn't spoken, or nothing was in
     * reach). The caller is responsible for charging the caster's own
     * reserves for whatever this DIDN'T cover.
     */
    public static float gather(ServerPlayer caster, SpellComposition composition, float requestedCost) {
        boolean brynnaSpoken = composition.words().stream().anyMatch(w -> "brynna".equals(w.trueName()));
        if (!brynnaSpoken || requestedCost <= 0f) {
            return 0f;
        }
        if (!(caster.level() instanceof ServerLevel level)) {
            return 0f;
        }

        double areaRadius = composition.scopeWord().map(Word::scopeRadius).filter(r -> r > 0f).orElse(0f);
        Vec3 origin = caster.position();

        List<LivingEntity> candidates;
        if (areaRadius > 0) {
            candidates = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(origin, origin).inflate(areaRadius),
                    e -> e != caster && e.isAlive());
            candidates.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(origin)));
        } else {
            candidates = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(origin, origin).inflate(DEFAULT_SINGLE_TARGET_RANGE),
                    e -> e != caster && e.isAlive());
            candidates.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(origin)));
            if (!candidates.isEmpty()) {
                candidates = candidates.subList(0, 1);
            }
        }

        float remaining = requestedCost;
        float gathered = 0f;
        for (LivingEntity candidate : candidates) {
            if (remaining <= 0f) {
                break;
            }
            LifeForceDrain.Outcome outcome = LifeForceDrain.drain(candidate, remaining, level);
            if (outcome.energyGathered() > 0f) {
                gathered += outcome.energyGathered();
                remaining -= outcome.energyGathered();
                Vec3 candidatePos = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0);
                SpellFx.trail(level, DragonSpeechParticles.DUST, 0x8a6a3b, 0x2a2050, candidatePos, origin.add(0, caster.getBbHeight() * 0.5, 0), 0.3);
            }
        }

        if (gathered > 0f) {
            SpellFx.spiral(level, DragonSpeechParticles.SPARKLE, 0x8a6a3b, 0xffffff, caster, 0.5);
        }

        return gathered;
    }
}
