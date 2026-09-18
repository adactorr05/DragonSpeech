package com.dragonspeech.enchant;

/** WARD/BLESSING/CURSE - the three kinds of custom (never table/anvil/villager) enchantment this mod adds. Vanilla enchantments (Sharpness etc.) are a completely separate system - see the class doc on MagicEnchantment for why they don't share this data structure. */
public enum EnchantmentKind {
    WARD,
    BLESSING,
    CURSE
}
