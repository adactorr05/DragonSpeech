package com.dragonspeech.enchant;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * wordId -> vanilla enchantment ResourceLocation, for every vanilla
 * enchant word this mod has built so far - the single source of truth
 * both EffectHandlerRegistry (registering each handler instance) and
 * ApplyVanillaEnchantEffectHandler (resolving a conflicting word's own
 * enchant id for exclusivity checks) read from, so the mapping only
 * ever needs updating in one place when a new vanilla enchant word gets
 * added.
 */
public final class VanillaEnchantWords {

    private static final Map<String, ResourceLocation> WORD_TO_ENCHANT = Map.ofEntries(
        Map.entry("hvassa", ResourceLocation.withDefaultNamespace("sharpness")),
        Map.entry("hlifd", ResourceLocation.withDefaultNamespace("protection")),
        Map.entry("seigla", ResourceLocation.withDefaultNamespace("unbreaking")),
        Map.entry("leikni", ResourceLocation.withDefaultNamespace("efficiency")),
        Map.entry("gaefa", ResourceLocation.withDefaultNamespace("fortune")),
        Map.entry("herfang", ResourceLocation.withDefaultNamespace("looting")),
        Map.entry("hrinda", ResourceLocation.withDefaultNamespace("knockback")),
        Map.entry("eldbit", ResourceLocation.withDefaultNamespace("fire_aspect")),
        Map.entry("laekning", ResourceLocation.withDefaultNamespace("mending")),
        Map.entry("mjukhond", ResourceLocation.withDefaultNamespace("silk_touch"))
    );

    private VanillaEnchantWords() {}

    public static ResourceLocation enchantIdFor(String wordId) {
        return WORD_TO_ENCHANT.get(wordId);
    }

    public static Map<String, ResourceLocation> all() {
        return WORD_TO_ENCHANT;
    }
}
