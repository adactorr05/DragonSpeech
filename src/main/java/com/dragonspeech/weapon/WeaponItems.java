package com.dragonspeech.weapon;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Maps a (ToolMaterial, ToolType) pair to the real vanilla item it should
 * look/act like when it's picked up off the ground after a hurled-weapon
 * projectile sticks somewhere. Deliberately reuses existing vanilla items
 * rather than registering brand new ones - this project has no art
 * pipeline available to author new item textures/models, and reusing a
 * vanilla item guarantees the projectile always has a real, already-
 * textured item to render and drop, with zero missing-texture risk.
 *
 * TRIDENT SPECIAL CASE: vanilla only has one, non-tiered Items.TRIDENT -
 * every material still changes the THROWN DAMAGE (see ToolMaterial/
 * HurlWeaponEffectHandler), it just can't change which trident model
 * gets picked up, since no tiered trident items exist to swap to.
 *
 * COPPER was removed from this system entirely (see ToolMaterial) since
 * 1.21.1 has no vanilla copper tool items to represent it with - add a
 * COPPER arm back here (and to ToolMaterial) if this project ever ports
 * to a version with real copper tools.
 */
public final class WeaponItems {

    private WeaponItems() {}

    /** A fresh single-count stack of whatever item best represents this material+tool pairing. */
    public static ItemStack stackFor(ToolMaterial material, ToolType type) {
        return new ItemStack(canonicalItem(material, type));
    }

    /**
     * The exact vanilla Item a (material, tool) pairing corresponds to -
     * used both to build a display/pickup stack (stackFor) and, by
     * HurlWeaponEffectHandler's "taka" handling, to find a matching real
     * item already sitting in the caster's own inventory.
     */
    public static Item canonicalItem(ToolMaterial material, ToolType type) {
        if (type == ToolType.TRIDENT) {
            return Items.TRIDENT;
        }
        return vanillaItem(material, type);
    }

    private static Item vanillaItem(ToolMaterial material, ToolType type) {
        return switch (material) {
            case WOOD -> switch (type) {
                case SWORD -> Items.WOODEN_SWORD;
                case AXE -> Items.WOODEN_AXE;
                case PICKAXE -> Items.WOODEN_PICKAXE;
                case SHOVEL -> Items.WOODEN_SHOVEL;
                case HOE -> Items.WOODEN_HOE;
                case TRIDENT -> Items.TRIDENT;
            };
            case GOLD -> switch (type) {
                case SWORD -> Items.GOLDEN_SWORD;
                case AXE -> Items.GOLDEN_AXE;
                case PICKAXE -> Items.GOLDEN_PICKAXE;
                case SHOVEL -> Items.GOLDEN_SHOVEL;
                case HOE -> Items.GOLDEN_HOE;
                case TRIDENT -> Items.TRIDENT;
            };
            case STONE -> switch (type) {
                case SWORD -> Items.STONE_SWORD;
                case AXE -> Items.STONE_AXE;
                case PICKAXE -> Items.STONE_PICKAXE;
                case SHOVEL -> Items.STONE_SHOVEL;
                case HOE -> Items.STONE_HOE;
                case TRIDENT -> Items.TRIDENT;
            };
            case IRON -> switch (type) {
                case SWORD -> Items.IRON_SWORD;
                case AXE -> Items.IRON_AXE;
                case PICKAXE -> Items.IRON_PICKAXE;
                case SHOVEL -> Items.IRON_SHOVEL;
                case HOE -> Items.IRON_HOE;
                case TRIDENT -> Items.TRIDENT;
            };
            case DIAMOND -> switch (type) {
                case SWORD -> Items.DIAMOND_SWORD;
                case AXE -> Items.DIAMOND_AXE;
                case PICKAXE -> Items.DIAMOND_PICKAXE;
                case SHOVEL -> Items.DIAMOND_SHOVEL;
                case HOE -> Items.DIAMOND_HOE;
                case TRIDENT -> Items.TRIDENT;
            };
            case NETHERITE -> switch (type) {
                case SWORD -> Items.NETHERITE_SWORD;
                case AXE -> Items.NETHERITE_AXE;
                case PICKAXE -> Items.NETHERITE_PICKAXE;
                case SHOVEL -> Items.NETHERITE_SHOVEL;
                case HOE -> Items.NETHERITE_HOE;
                case TRIDENT -> Items.TRIDENT;
            };
        };
    }
}
