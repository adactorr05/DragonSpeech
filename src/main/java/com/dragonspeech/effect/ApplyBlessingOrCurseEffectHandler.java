package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.enchant.EnchantmentKind;
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
 * One reusable EffectHandler class backing ALL blessing and curse words -
 * registered once PER WORD (see EffectHandlerRegistry), each instance
 * configured with its own id/wordId/kind/maxLevel/message rather than
 * writing 14 near-identical handler classes. This is the SAME "words
 * compose instead of the codebase multiplying" instinct applied to Java
 * classes instead of grammar.
 *
 * DELIBERATELY UNREMOVABLE - unlike ApplyWardEnchantEffectHandler, this
 * never checks for "letta" at all. Blessings and curses are permanent
 * once applied, no exceptions, per spec - there's no dispel branch to
 * even have a bug in.
 *
 * LEVELS: casting the SAME blessing/curse word again on an item that
 * already carries it INCREASES its level by 1, up to maxLevel (1 for
 * every curse except Hiding - not yet built, see its own note
 * elsewhere - and 1-3 for blessings depending on which one, matching the
 * exact numbers given). Once at maxLevel, recasting fails outright
 * rather than silently doing nothing, so the caster gets clear feedback
 * instead of wondering if the massive stamina cost just vanished for
 * nothing (it doesn't get charged at all in that case - the failure
 * happens before payment, same as every other pre-flight refusal in this
 * mod).
 *
 * WHAT THIS DOES: gets the entry onto the item and tracked, nothing
 * more. The actual MECHANICAL EFFECT of any given blessing/curse (faster
 * regen, a permanent mark, whatever) lives elsewhere, reading back
 * through EquippedEnchantments - this class doesn't know or care what
 * its own words DO, only that they're recorded.
 */
public class ApplyBlessingOrCurseEffectHandler implements EffectHandler {

    private static final float ENCHANT_BASE_COST = 120f;

    private final ResourceLocation id;
    private final String wordId;
    private final EnchantmentKind kind;
    private final int maxLevel;
    private final String applyMessage;

    private final EffectHandlerCaps caps;

    public ApplyBlessingOrCurseEffectHandler(String handlerId, String wordId, EnchantmentKind kind, int maxLevel, String applyMessage) {
        this.id = DragonSpeech.id(handlerId);
        this.wordId = wordId;
        this.kind = kind;
        this.maxLevel = maxLevel;
        this.applyMessage = applyMessage;
        this.caps = new EffectHandlerCaps(1, ENCHANT_BASE_COST, 1f, Set.of(TargetKind.ENTITY));
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public EffectHandlerCaps caps() {
        return caps;
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
        Optional<MagicEnchantment> current = existing.find(wordId);

        if (current.isPresent() && current.get().level() >= maxLevel) {
            return EffectResult.failure("This already holds all the strength it can - speaking the word again changes nothing.");
        }

        int newLevel = current.map(e -> e.level() + 1).orElse(1);
        MagicEnchantment entry = kind == EnchantmentKind.BLESSING
            ? MagicEnchantment.newBlessing(wordId, newLevel)
            : MagicEnchantment.newCurse(wordId, newLevel);

        MagicEnchantments updated = existing.has(wordId)
            ? existing.replacing(wordId, entry)
            : existing.with(entry);

        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, updated);

        String levelNote = maxLevel > 1 ? " (level " + newLevel + " of " + maxLevel + ")" : "";
        return EffectResult.success(1, applyMessage + levelNote);
    }
}
