package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.enchant.MagicEnchantment;
import com.dragonspeech.enchant.MagicEnchantments;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.storage.SkillsAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;

/**
 * Backs "duljabol" (Curse of Hiding) - genuinely different shape from
 * every other blessing/curse, so it gets its own dedicated handler
 * rather than reusing ApplyBlessingOrCurseEffectHandler:
 *
 *   - You choose WHAT it hides, by speaking BOTH duljabol AND the
 *     specific word for the enchantment you want concealed, in the
 *     same sentence (e.g. "duljabol ennisbol" hides the Marked Brow
 *     curse). Fails outright - "the shape of the word slips away,
 *     unfinished" - if the item doesn't currently carry that
 *     enchantment, OR if the sentence doesn't name one at all. This is
 *     the literal mechanic behind the spec's own words: "the
 *     enchantment will fail if it does not see what enchantment it is
 *     supposed to hide."
 *   - No level cap, unlike every other blessing/curse - "each level
 *     allows it to hide 1 curse/blessing," so casting it again (this
 *     time naming a DIFFERENT already-present enchantment) raises its
 *     own level by 1 and hides that one too. There is no maximum;
 *     ApplyBlessingOrCurseEffectHandler's level-cap logic genuinely
 *     doesn't fit here, which is the other reason this needed its own
 *     class rather than being folded into that one.
 *   - Cannot target itself or another already-hidden entry - but CAN
 *     target a ward (an earlier version of this spec exempted wards,
 *     since explicitly reversed - wards can be concealed too now).
 *   - Permanent and unremovable, same as every curse - no letta branch
 *     exists here at all.
 *
 * See MagicEnchantmentTooltips for the other half of this: duljabol's
 * own tooltip line always shows (proving something is hidden) but never
 * shows its level (so the VIEWER can't tell how many things are
 * concealed), while whatever it's hidden doesn't render a line at all.
 */
public class ApplyHidingCurseEffectHandler implements EffectHandler {

    private static final float ENCHANT_BASE_COST = 120f;
    private static final String WORD_ID = "duljabol";

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, ENCHANT_BASE_COST, 1f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("apply_duljabol");
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
        return ENCHANT_BASE_COST;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();

        if (!SkillsAccess.get(caster).canEnchant()) {
            return EffectResult.failure(
                "You do not yet know how to bind a working into an object - the shape of the word slips away from you.");
        }

        ItemStack held = caster.getMainHandItem();
        if (held.isEmpty()) {
            return EffectResult.failure("You hold nothing to bind a working into.");
        }

        MagicEnchantments existing = held.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);

        Optional<MagicEnchantment> target = invocation.composition().words().stream()
            .map(w -> w.trueName())
            .filter(name -> !name.equals(WORD_ID))
            .map(existing::find)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(e -> !e.hidden())
            .findFirst();

        if (target.isEmpty()) {
            return EffectResult.failure(
                "You must name a working already bound here to conceal it.");
        }

        MagicEnchantments afterHiding = existing.replacing(target.get().wordId(), target.get().withHidden(true));

        Optional<MagicEnchantment> hidingEntry = afterHiding.find(WORD_ID);
        int newLevel = hidingEntry.map(e -> e.level() + 1).orElse(1);
        MagicEnchantment hidingCurse = MagicEnchantment.newCurse(WORD_ID, newLevel);
        MagicEnchantments finalEnchantments = afterHiding.has(WORD_ID)
            ? afterHiding.replacing(WORD_ID, hidingCurse)
            : afterHiding.with(hidingCurse);

        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, finalEnchantments);

        return EffectResult.success(1, "What was bound here sinks out of sight - still bound, still working, just no longer seen.");
    }
}
