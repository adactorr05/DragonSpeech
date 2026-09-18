package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.spell.CostBreakdown;
import com.dragonspeech.spell.EffortContext;
import com.dragonspeech.spell.SpellCostCalculator;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * The glue between Phase 1 (words), Phase 2 (cost), and this effect
 * framework: given a resolved EffectInvocation, finds the handler its VERB
 * word points to, checks that handler's hard caps, and produces a priced
 * PreparedCast. This is the ONE place those three pieces come together -
 * the casting grid and the guess resolver should both funnel through here
 * rather than duplicating this logic.
 *
 * Note: a composition made up only of a CONTROL word (the stop-word) isn't
 * a new cast at all - it's meant to interrupt an already-active channeled
 * spell. That's the (Phase 2 item 7) channeled-spell system's job to
 * intercept before it ever reaches this resolver.
 */
public final class SpellCastResolver {

    private SpellCastResolver() {}

    public static PreparedCast prepare(EffectInvocation invocation, float distanceFromCaster, float domainAttunementMultiplier) {
        Optional<Word> verb = invocation.composition().wordsOf(WordCategory.VERB).stream().findFirst();
        if (verb.isEmpty()) {
            return PreparedCast.rejected("No verb word present - nothing to cast.");
        }

        Optional<ResourceLocation> handlerId = verb.get().effectHandlerId();
        if (handlerId.isEmpty()) {
            return PreparedCast.rejected("The word '" + verb.get().meaning() + "' has no effect bound to it yet.");
        }

        Optional<EffectHandler> handlerLookup = EffectHandlerRegistry.get(handlerId.get());
        if (handlerLookup.isEmpty()) {
            DragonSpeech.LOGGER.warn("[DragonSpeech] Verb '{}' references unknown effect handler '{}'",
                verb.get().trueName(), handlerId.get());
            return PreparedCast.rejected("This word's effect could not be found.");
        }
        EffectHandler handler = handlerLookup.get();

        Optional<String> targetViolation = handler.caps().validateTargets(invocation);
        if (targetViolation.isPresent()) {
            return PreparedCast.rejected(targetViolation.get());
        }

        Optional<String> rangeViolation = handler.caps().validateRange(distanceFromCaster);
        if (rangeViolation.isPresent()) {
            return PreparedCast.rejected(rangeViolation.get());
        }

        float baseMagnitude = handler.estimateBaseMagnitude(invocation);
        boolean permanent = !invocation.composition().wordsOf(WordCategory.BINDING).isEmpty();
        EffortContext context = new EffortContext(baseMagnitude, distanceFromCaster, permanent, invocation.targets().size());

        CostBreakdown cost = SpellCostCalculator.calculate(invocation.composition(), context, domainAttunementMultiplier);

        return PreparedCast.accepted(handler, invocation, cost);
    }
}
