package com.dragonspeech.cast;

import com.dragonspeech.effect.EffectInvocation;
import com.dragonspeech.effect.EffectResult;
import com.dragonspeech.effect.PreparedCast;
import com.dragonspeech.stamina.DrainResolver;
import com.dragonspeech.stamina.DrainResult;
import com.dragonspeech.stamina.NearbyFundingDrain;

/**
 * The single point where a costed cast actually happens. This is the only
 * place in the whole mod that should call an EffectHandler's apply() - by
 * routing every cast through here, we guarantee the order is always
 * "validate caps -> pay cost -> only then execute," never the reverse.
 *
 * This is now the complete pipeline: word choice -> grammar -> cost ->
 * caps -> payment -> capped effect. The casting grid GUI, the guess
 * resolver, and chat-casting should all end up calling
 * SpellCastResolver.prepare(...) followed by CastExecutor.execute(...) -
 * none of them should touch DrainResolver or an EffectHandler directly.
 *
 * "brynna" - see NearbyFundingDrain - can cover part or all of the cost
 * from nearby life force before the caster's own reserves are ever
 * touched. Checked here rather than inside DrainResolver itself so that
 * DrainResolver's own cascade (used by several OTHER payment call sites
 * that have no business draining bystanders - ward defense, backlash,
 * channel upkeep) stays completely unaffected by this.
 *
 * "telja" - whether THIS cast is allowed to reach into the caster's
 * ordinary inventory for charged items, on top of whatever their
 * equipped accessory slots already cover automatically. See
 * DrainResolver.applyDrain's 3-arg overload and ItemStaminaStorage's
 * drainAccessories/drainGeneralInventory split for why those are two
 * separate tiers now instead of one.
 */
public final class CastExecutor {

    private CastExecutor() {}

    public static CastOutcome execute(PreparedCast preparedCast) {
        if (!preparedCast.accepted()) {
            return CastOutcome.rejected(preparedCast.rejectionReason());
        }

        EffectInvocation invocation = preparedCast.invocation();
        float cost = preparedCast.cost().finalCost() * com.dragonspeech.config.DragonSpeechConfig.costMultiplier()
                * com.dragonspeech.wow.WordOfWordsRules.magicCostMultiplier(invocation.caster().getServer())
                * com.dragonspeech.wow.WordOfWordsKnowledge.sentenceCostMultiplier(invocation.composition())
                // Config GUI (Server tab) "Spell Cost Multiplier" - independent of difficulty's own costMultiplier() above, layered on top.
                * com.dragonspeech.config.DragonSpeechConfig.costMultiplierExtra();

        // Curse of the Hungry Word ("hungrord") - +25% to every cast's
        // cost while worn. Deliberately checked here rather than folded
        // into costMultiplier() - that's a DIFFICULTY setting (one value,
        // same for the whole world), this is per-player equipment.
        if (com.dragonspeech.enchant.EquippedEnchantments.has(invocation.caster(), "hungrord")) {
            cost *= 1.25f;
        }

        float gatheredFromNearby = NearbyFundingDrain.gather(invocation.caster(), invocation.composition(), cost);
        float remainingCost = Math.max(0f, cost - gatheredFromNearby);

        boolean telja = invocation.composition().words().stream().anyMatch(w -> "telja".equals(w.trueName()));
        DrainResult drain = DrainResolver.applyDrain(invocation.caster(), remainingCost, telja);

        if (!drain.succeeded()) {
            return CastOutcome.overdrafted(drain);
        }

        EffectResult effectResult = preparedCast.handler().apply(invocation);
        return CastOutcome.executed(drain, effectResult);
    }
}
