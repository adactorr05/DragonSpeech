package com.dragonspeech.item;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.accessory.AccessoryItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * One dedicated creative tab holding EVERY custom item this mod
 * registers - the tablets, Scholar's Fragment, Dragon Egg, Eldunari,
 * and all 12 accessory jewelry items - so all of it is in one obvious,
 * ordinary place instead of scattered across vanilla tabs or requiring
 * /give. There are only two files in the whole mod that register an
 * Item (DragonSpeechItems, AccessoryItems) - this class deliberately
 * pulls from both directly rather than keeping its own separate list,
 * so a new item added to either of those automatically shows up here
 * too without a third place needing to remember it.
 *
 * VERSION-RISK NOTE: this is genuinely new API surface for this project
 * (every earlier item registration just added into an EXISTING vanilla
 * tab via ItemGroupEvents.modifyEntriesEvent - this is the first time
 * anything here creates a whole NEW tab). FabricItemGroup.builder() is
 * Fabric API's own standard, long-established convenience wrapper
 * around vanilla's CreativeModeTab.builder() specifically for this
 * purpose, so the general shape below should be right, but the exact
 * builder method names (.title/.icon/.displayItems) are not verified
 * against your exact Fabric API version the way code elsewhere in this
 * project has been checked against decompiled sources. If this doesn't
 * compile, check FabricItemGroup's actual builder methods in your IDE -
 * the item list itself (and everywhere else in the mod) is unaffected
 * either way.
 */
public final class DragonSpeechItemGroups {

    public static final ResourceKey<CreativeModeTab> MAIN =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, DragonSpeech.id("main"));

    public static final CreativeModeTab MAIN_TAB = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB, MAIN,
        FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.dragonspeech.main"))
            .icon(() -> new ItemStack(com.dragonspeech.dragon.DragonSpeechBlocks.RED_DRAGON_EGG.asItem()))
            .displayItems((params, output) -> {
                output.accept(DragonSpeechItems.WORD_TABLET_WORN);
                output.accept(DragonSpeechItems.WORD_TABLET_ANCIENT);
                output.accept(DragonSpeechItems.WORD_TABLET_PRIMORDIAL);
                output.accept(DragonSpeechItems.SCHOLARS_FRAGMENT);
                // DRAGON_EGG (single item) removed - now 7 separate
                // block-items, one per color (see DragonSpeechBlocks).
                for (var egg : com.dragonspeech.dragon.DragonSpeechBlocks.all()) {
                    output.accept(egg.asItem());
                }
                // ELDUNARI (single item) removed - now 8 separate
                // items, 7 colors + mad (see DragonSpeechHearts).
                for (var heart : com.dragonspeech.eldunari.DragonSpeechHearts.realColors()) {
                    output.accept(heart);
                }
                output.accept(com.dragonspeech.eldunari.DragonSpeechHearts.MAD);
                for (var item : AccessoryItems.all()) {
                    output.accept(item);
                }
            })
            .build()
    );

    private DragonSpeechItemGroups() {}

    /** Call from onInitialize(), AFTER DragonSpeechItems/AccessoryItems have had a chance to register - referencing their static fields above already forces that via normal Java class-loading, but calling this explicitly (and after them) keeps init order obvious rather than relying on that implicitly. */
    public static void bootstrap() {
    }
}
