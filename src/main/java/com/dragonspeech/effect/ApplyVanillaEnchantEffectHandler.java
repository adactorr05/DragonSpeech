package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.enchant.ItemCategory;
import com.dragonspeech.enchant.VanillaEnchantWords;
import com.dragonspeech.storage.SkillsAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Set;

/**
 * Applies a REAL vanilla enchantment (Sharpness, Protection, etc.)
 * through vanilla's own actual ItemEnchantments component - deliberately
 * NOT the custom MagicEnchantments system wards/blessings/curses use
 * (see MagicEnchantment's own class doc for why those two systems are
 * kept separate). Using the real component means the enchantment shows
 * up in the tooltip automatically via vanilla's own rendering, and any
 * other mod/vanilla system that reads enchantments (combat damage,
 * mining speed, etc.) sees it too - none of that had to be built here.
 *
 * NEVER via enchantment table, anvil, or villager - the only way onto
 * an item is speaking the specific word, same as every other
 * enchantment in this system.
 *
 * ================================================================
 * GENUINE VERSION-RISK NOTE - READ BEFORE DEBUGGING A FAILURE HERE
 * ================================================================
 * This file touches API surface with NO proven reference anywhere else
 * in this project (confirmed by searching before writing a line of
 * this - "ItemEnchantments" only appeared in my own earlier doc
 * comments, never in working code). Every piece below is used with
 * real, reasoned confidence, but none of it has compiled in this
 * specific project before:
 *
 *   - Registry lookup: registryAccess().registryOrThrow(Registries.ENCHANTMENT)
 *     .getHolder(ResourceKey.create(...)) - the standard modern pattern
 *     for looking up a data-driven registry entry by id.
 *   - Applying: stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY),
 *     wrapped in ItemEnchantments.Mutable, .set(holder, level), then
 *     .toImmutable() written back - the standard modern pattern for
 *     mutating an item's enchantment component.
 *   - getMaxLevel() on Enchantment - long-stable across many versions,
 *     the one piece of this file I'm confident predates the 1.20.5
 *     component rewrite entirely.
 *
 * If any of this doesn't compile, the fix is almost certainly a method
 * RENAME on the same underlying concept (e.g. getHolder vs get, or a
 * different Mutable constructor signature) rather than a wrong overall
 * approach - check ItemEnchantments/Enchantment/HolderLookup in your
 * decompiled sources for the current exact names.
 *
 * EXCLUSIVITY IS HARDCODED, NOT READ FROM VANILLA: rather than guess at
 * vanilla's own compatibility-check API (genuinely uncertain which
 * exact method exposes this in the current data-driven enchantment
 * system), each word explicitly lists which OTHER enchant word-ids it
 * conflicts with (see EXCLUSIVE_WITH below) - 100% within this file's
 * own control, at the cost of needing to extend this table by hand as
 * more vanilla enchant words get added later.
 */
public class ApplyVanillaEnchantEffectHandler implements EffectHandler {

    private static final float ENCHANT_BASE_COST = 100f;

    /** wordId -> other wordIds it cannot coexist with on the same item. Populated only where a real vanilla conflict exists among the words actually built so far. */
    private static final java.util.Map<String, Set<String>> EXCLUSIVE_WITH = java.util.Map.of(
        "mjukhond", Set.of("gaefa"), // Silk Touch excludes Fortune, same as real vanilla
        "gaefa", Set.of("mjukhond")
    );

    private final ResourceLocation id;
    private final String wordId;
    private final ResourceLocation vanillaEnchantId;
    private final ItemCategory category;
    private final EffectHandlerCaps caps;

    public ApplyVanillaEnchantEffectHandler(String handlerId, String wordId, ResourceLocation vanillaEnchantId, ItemCategory category) {
        this.id = DragonSpeech.id(handlerId);
        this.wordId = wordId;
        this.vanillaEnchantId = vanillaEnchantId;
        this.category = category;
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
        boolean dispel = invocation.composition().words().stream().anyMatch(w -> "letta".equals(w.trueName()));
        return dispel ? 2f : ENCHANT_BASE_COST;
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

        boolean dispel = invocation.composition().words().stream().anyMatch(w -> "letta".equals(w.trueName()));
        if (dispel) {
            return applyDispel(held);
        }

        if (held.getItem() instanceof com.dragonspeech.enchant.MagicEnchantableItem) {
            return EffectResult.failure("Jewelry channels a different kind of working - this one will not take.");
        }
        if (!category.matches(held)) {
            return EffectResult.failure("What you hold is not the kind of thing this working shapes itself to.");
        }

        var registryAccess = caster.level().registryAccess();
        var enchantRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder = enchantRegistry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, vanillaEnchantId))
            .orElse(null);
        if (holder == null) {
            return EffectResult.failure("The working finds nothing to answer it - something is wrong with the binding itself.");
        }

        ItemEnchantments current = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (String conflictingWord : EXCLUSIVE_WITH.getOrDefault(wordId, Set.of())) {
            ResourceLocation conflictingId = VanillaEnchantWords.enchantIdFor(conflictingWord);
            if (conflictingId != null && current.keySet().stream().anyMatch(h -> h.is(conflictingId))) {
                return EffectResult.failure("What you hold already carries a working this one cannot share space with.");
            }
        }

        int currentLevel = current.getLevel(holder);
        int maxLevel = holder.value().getMaxLevel();
        if (currentLevel >= maxLevel) {
            return EffectResult.failure("This already holds the working as strongly as it can - speaking the word again changes nothing.");
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(holder, currentLevel + 1);
        held.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());

        return EffectResult.success(1, "The working takes hold, binding itself into what you carry.");
    }

    /** "letta [word]" removal - rebuilds the enchantment map excluding just this one, rather than guessing at a dedicated removal method on ItemEnchantments.Mutable I couldn't verify exists. */
    private EffectResult applyDispel(ItemStack held) {
        ItemEnchantments current = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.keySet().stream().noneMatch(h -> h.is(vanillaEnchantId))) {
            return EffectResult.failure("What you hold carries no such working to release.");
        }

        ItemEnchantments.Mutable rebuilt = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Holder<Enchantment> existing : current.keySet()) {
            if (!existing.is(vanillaEnchantId)) {
                rebuilt.set(existing, current.getLevel(existing));
            }
        }
        held.set(DataComponents.ENCHANTMENTS, rebuilt.toImmutable());
        return EffectResult.success(1, "The working unwinds from what you hold and fades.");
    }
}
