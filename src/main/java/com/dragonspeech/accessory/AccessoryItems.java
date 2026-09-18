package com.dragonspeech.accessory;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.storage.StorageMediumRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 12 jewelry items actually included (RING/NECKLACE/BELT across
 * GOLD/EMERALD/DIAMOND/NETHERITE/MIXED, minus gold_gem_belt,
 * netherite_gem_belt, and mixed_necklace - no real texture was made for
 * those three, so they were removed rather than shipped with a
 * placeholder). Wearable in the 4 accessory slots (see AccessorySlot -
 * unrestricted for now, so any of these, or honestly any item at all,
 * fits any slot until a real "is this jewelry" tag exists to narrow
 * that).
 *
 * mixed_gem_belt's real recipe (see the recipe JSON, not this file) uses
 * six distinct materials - leather, gold, diamond, emerald, redstone,
 * netherite - not the four-material pattern the other MIXED items use.
 *
 * WHAT THESE DO RIGHT NOW: each is registered as a StorageMediumRegistry
 * medium - a much bigger stamina reservoir than a raw gem, worn instead
 * of just carried, feeding a cast automatically once equipped (see
 * ItemStaminaStorage.drainAccessories). WHAT THEY DON'T DO YET: the
 * ward/blessing/curse enchantment system from the fuller request -
 * that's real, separate follow-up work (a new enchantment registry, the
 * enchantment-casting skill, durability-vs-stamina-linked ward variants)
 * layered on top of these same items later, not built here. These are
 * fully real, craftable, capacity-holding jewelry today; they just
 * aren't enchantment-carriers yet.
 *
 * CAPACITY TABLE: belt > necklace > ring at every material tier (more
 * physical bulk, more capacity), and MIXED is deliberately the best
 * across the board - both matching what was actually asked for.
 */
public final class AccessoryItems {

    private AccessoryItems() {}

    private record Entry(String id, AccessorySlotType slot, AccessoryMaterial material, float capacity, float decayPerMinute) {}

    private static final java.util.List<Entry> ENTRIES = java.util.List.of(
        new Entry("gold_ring", AccessorySlotType.RING, AccessoryMaterial.GOLD, 150f, 0.5f),
        new Entry("gold_necklace", AccessorySlotType.NECKLACE, AccessoryMaterial.GOLD, 250f, 0.5f),

        new Entry("emerald_ring", AccessorySlotType.RING, AccessoryMaterial.EMERALD, 350f, 0f),
        new Entry("emerald_necklace", AccessorySlotType.NECKLACE, AccessoryMaterial.EMERALD, 550f, 0f),
        new Entry("emerald_gem_belt", AccessorySlotType.BELT, AccessoryMaterial.EMERALD, 850f, 0f),

        new Entry("diamond_ring", AccessorySlotType.RING, AccessoryMaterial.DIAMOND, 650f, 0f),
        new Entry("diamond_necklace", AccessorySlotType.NECKLACE, AccessoryMaterial.DIAMOND, 950f, 0f),
        new Entry("diamond_gem_belt", AccessorySlotType.BELT, AccessoryMaterial.DIAMOND, 1400f, 0f),

        new Entry("netherite_ring", AccessorySlotType.RING, AccessoryMaterial.NETHERITE, 1100f, 0f),
        new Entry("netherite_necklace", AccessorySlotType.NECKLACE, AccessoryMaterial.NETHERITE, 1600f, 0f),

        new Entry("mixed_ring", AccessorySlotType.RING, AccessoryMaterial.MIXED, 1800f, 0f),
        new Entry("mixed_gem_belt", AccessorySlotType.BELT, AccessoryMaterial.MIXED, 3800f, 0f)
    );

    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    static {
        for (Entry entry : ENTRIES) {
            Item item = Registry.register(
                BuiltInRegistries.ITEM, DragonSpeech.id(entry.id()),
                new com.dragonspeech.enchant.MagicEnchantableItem(new Item.Properties().stacksTo(1).rarity(rarityFor(entry.material())))
            );
            ITEMS.put(entry.id(), item);
            StorageMediumRegistry.register(item, new StorageMediumRegistry.MediumProperties(entry.capacity(), entry.decayPerMinute()));
        }
    }

    private static net.minecraft.world.item.Rarity rarityFor(AccessoryMaterial material) {
        return switch (material) {
            case GOLD -> net.minecraft.world.item.Rarity.COMMON;
            case EMERALD, DIAMOND -> net.minecraft.world.item.Rarity.UNCOMMON;
            case NETHERITE -> net.minecraft.world.item.Rarity.RARE;
            case MIXED -> net.minecraft.world.item.Rarity.EPIC;
        };
    }

    public static Item get(String id) {
        Item item = ITEMS.get(id);
        if (item == null) {
            throw new IllegalArgumentException("No accessory item registered with id '" + id + "'");
        }
        return item;
    }

    /** Every registered accessory item, in registration order - used by DragonSpeechItemGroups to populate the mod's dedicated creative tab. */
    public static java.util.Collection<Item> all() {
        return ITEMS.values();
    }

    /** Call from onInitialize() to force this class's static fields (and therefore registration) to run. Creative-tab placement now happens in DragonSpeechItemGroups instead of here - these used to be added to vanilla's Tools & Utilities tab, but now live in the mod's own dedicated tab so everything is in one findable place. */
    public static void bootstrap() {
    }
}
