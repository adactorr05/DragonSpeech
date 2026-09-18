package com.dragonspeech.eldunari;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.dragon.DragonColor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registers all 8 Dragon Heart items - one per DragonColor plus the mad
 * variant. Replaces the single DragonSpeechItems.ELDUNARI (removed - see
 * that class's own note).
 */
public final class DragonSpeechHearts {

    // FIX: this map MUST be declared (and therefore initialized) before
    // any of the 8 registerHeart() calls below - Java runs static field
    // initializers top-to-bottom in DECLARATION order, and registerHeart()
    // writes into this map. Same exact bug, same exact fix, as
    // DragonSpeechBlocks needed a few rounds back - I made the identical
    // mistake again here without checking against that earlier fix first.
    private static final Map<DragonColor, Item> BY_COLOR = new EnumMap<>(DragonColor.class);

    public static final Item RED = registerHeart("dragon_heart_red", DragonColor.RED);
    public static final Item BRONZE = registerHeart("dragon_heart_bronze", DragonColor.BRONZE);
    public static final Item BLUE = registerHeart("dragon_heart_blue", DragonColor.BLUE);
    public static final Item WHITE = registerHeart("dragon_heart_white", DragonColor.WHITE);
    public static final Item GREEN = registerHeart("dragon_heart_green", DragonColor.GREEN);
    public static final Item BLACK = registerHeart("dragon_heart_black", DragonColor.BLACK);
    public static final Item ENDER = registerHeart("dragon_heart_ender", DragonColor.ENDER);
    public static final Item MAD = registerHeart("dragon_heart_mad", null);

    private DragonSpeechHearts() {}

    public static void bootstrap() {}

    public static Item byColor(DragonColor color) {
        return BY_COLOR.get(color);
    }

    /** The 7 real colors only - deliberately excludes MAD, since loot/reward logic should treat it as a separate, rarer roll rather than an equal 8th option (see LootInjection). */
    public static Item[] realColors() {
        return BY_COLOR.values().toArray(new Item[0]);
    }

    private static Item registerHeart(String id, DragonColor color) {
        Item item = Registry.register(
                BuiltInRegistries.ITEM,
                DragonSpeech.id(id),
                new DragonHeartItem(new Item.Properties().stacksTo(1), color)
        );
        if (color != null) {
            BY_COLOR.put(color, item);
        }
        return item;
    }
}