package com.dragonspeech.enchant;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Which real-item categories a vanilla enchantment word is allowed to
 * target - per explicit spec: "vanilla enchantments... only go on
 * tools/weapons/armor (protection goes on armor, sharpness doesn't but
 * does go on weapons/tools)" - and, separately, jewelry is EXEMPT from
 * vanilla enchants entirely (only wards/blessings/curses go on jewelry -
 * this enum is never even consulted for jewelry, since
 * ApplyVanillaEnchantEffectHandler's own item-type check runs first).
 *
 * DELIBERATE CHOICE: category membership is checked via real Java
 * instanceof against vanilla's own item class hierarchy (SwordItem,
 * DiggerItem, ArmorItem, etc.) rather than an item tag lookup. Both
 * approaches are legitimate; instanceof was chosen here because it's
 * the one I could verify with full confidence rather than guess at tag
 * names (#minecraft:swords, #minecraft:enchantable/sharp_weapon, etc. -
 * plausible but unconfirmed in this project's exact version). AxeItem
 * counts as BOTH weapon and tool, matching vanilla's own real dual
 * nature (axes can mine AND fight).
 */
public enum ItemCategory {
    WEAPON,
    TOOL,
    ARMOR,
    /** Unbreaking, Mending - genuinely apply to any of the three groups above, never to jewelry either. */
    UNIVERSAL;

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (this) {
            case WEAPON -> isWeapon(stack);
            case TOOL -> isTool(stack);
            case ARMOR -> stack.getItem() instanceof ArmorItem;
            case UNIVERSAL -> isWeapon(stack) || isTool(stack) || stack.getItem() instanceof ArmorItem;
        };
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof TridentItem || stack.getItem() instanceof AxeItem;
    }

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof DiggerItem; // covers pickaxe/axe/shovel/hoe under this project's mappings
    }
}
