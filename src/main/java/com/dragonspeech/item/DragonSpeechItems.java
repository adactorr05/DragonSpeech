package com.dragonspeech.item;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * VERSION-RISK NOTE: plain Registry.register into BuiltInRegistries.ITEM
 * is the long-standing, stable item registration path used here.
 * Creative-tab placement (previously done inline in this file's own
 * bootstrap()) now lives in DragonSpeechItemGroups instead, alongside
 * the accessory jewelry - see that class for the FabricItemGroup risk
 * note.
 */
public final class DragonSpeechItems {

    public static final Item WORD_TABLET_WORN = Registry.register(
        BuiltInRegistries.ITEM, DragonSpeech.id("word_tablet_worn"),
        new WordTabletItem(new Item.Properties(), 1));

    public static final Item WORD_TABLET_ANCIENT = Registry.register(
        BuiltInRegistries.ITEM, DragonSpeech.id("word_tablet_ancient"),
        new WordTabletItem(new Item.Properties(), 2));

    public static final Item WORD_TABLET_PRIMORDIAL = Registry.register(
        BuiltInRegistries.ITEM, DragonSpeech.id("word_tablet_primordial"),
        new WordTabletItem(new Item.Properties(), 3));

    public static final Item SCHOLARS_FRAGMENT = Registry.register(
        BuiltInRegistries.ITEM, DragonSpeech.id("scholars_fragment"),
        new ScholarsFragmentItem(new Item.Properties()));

    /** Purchased from Elves/Elder Elves/neutral Human Mages - see com.dragonspeech.mob.casting.MobTradeOffers. Right-click to learn the word it was bought for. */
    public static final Item WORD_SCROLL = Registry.register(
        BuiltInRegistries.ITEM, DragonSpeech.id("word_scroll"),
        new WordScrollItem(new Item.Properties().stacksTo(16)));

    // DRAGON_EGG removed - "I want it to be 7 eggs, 1 egg for each
    // color" per explicit direction. Replaced by 7 separate
    // VariantDragonEggBlock + DragonEggBlockItem pairs, registered in
    // DragonSpeechBlocks instead of here (they're blocks now, not a
    // plain Item).

    // ELDUNARI (single generic item) removed - "there also need to be
    // an eldunari for each color dragon... Eldunari needs to be renamed
    // to Dragon Heart" per explicit direction. Replaced by 8 separate
    // DragonHeartItem registrations (7 colors + mad) in
    // DragonSpeechHearts instead of here.

    private DragonSpeechItems() {}

    /** Call from onInitialize() to force this class's static fields (and therefore registration) to run. Creative-tab placement now happens in DragonSpeechItemGroups instead of here. */
    public static void bootstrap() {
    }
}
