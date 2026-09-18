package com.dragonspeech.loot;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

/**
 * Registers the custom loot function(s) this mod defines - currently
 * just BiasWordFragmentFunction, used by elven_trial_vault.json to bias
 * scholars_fragment word selection toward elven-trial vocabulary.
 *
 * VERSION-RISK NOTE: this is the first custom LootItemFunction built in
 * this project - the registration shape (Registry.register against
 * Registries.LOOT_FUNCTION_TYPE, wrapping the function's own MapCodec in
 * a LootItemFunctionType) matches the standard, long-stable vanilla
 * pattern every built-in loot function (SetCount, EnchantRandomly, etc.)
 * uses, but hasn't been verified against real 1.21.1 source the way the
 * structure-generation fix was earlier this session. If this doesn't
 * compile, checking LootItemFunctionType's exact constructor and
 * Registries.LOOT_FUNCTION_TYPE's exact name in your decompiled sources
 * is the first thing to check.
 */
public final class DragonSpeechLootFunctions {

    public static final LootItemFunctionType<BiasWordFragmentFunction> BIAS_ELVEN_TRIAL_FRAGMENT =
        Registry.register(
            BuiltInRegistries.LOOT_FUNCTION_TYPE,
            DragonSpeech.id("bias_elven_trial_fragment"),
            new LootItemFunctionType<>(BiasWordFragmentFunction.CODEC)
        );

    private DragonSpeechLootFunctions() {}

    /** Referencing the class is enough to trigger the static initializer above. */
    public static void bootstrap() {}
}
