package com.dragonspeech.loot;

import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.word.WordRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.ArrayList;
import java.util.List;

/**
 * "These elven trial chests should have scholar fragments that have
 * words from the elven trial. It can have some from other areas, but
 * more leaning toward words from the elven trial" per explicit
 * direction. ScholarsFragmentItem's own pickWord() is hardcoded toward
 * COMMON vocabulary (weighted heavily AWAY from elven trial - see that
 * class's own doc) since a generic, anywhere-found fragment shouldn't
 * usually carry deep vocabulary. That weighting is correct for every
 * OTHER location a fragment can appear - this loot function is what
 * lets elven_trial_vault specifically override it, by pre-setting
 * FRAGMENT_WORD via a loot function BEFORE the player ever picks it up,
 * so ScholarsFragmentItem.use()'s own lazy pick (which only runs if
 * FRAGMENT_WORD is unset) never triggers for these - the biased choice
 * this function makes is what sticks.
 */
public class BiasWordFragmentFunction extends LootItemConditionalFunction {

    public static final MapCodec<BiasWordFragmentFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
        commonFields(instance).apply(instance, BiasWordFragmentFunction::new));

    protected BiasWordFragmentFunction(List<LootItemCondition> predicates) {
        super(predicates);
    }

    @Override
    public LootItemFunctionType<BiasWordFragmentFunction> getType() {
        return DragonSpeechLootFunctions.BIAS_ELVEN_TRIAL_FRAGMENT;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        ResourceLocation wordId = pickBiasedWord(context.getRandom());
        stack.set(DragonSpeechComponents.FRAGMENT_WORD, wordId.toString());
        return stack;
    }

    /** Heavily favors elven-trial vocabulary while still occasionally landing on something else - "can have some from other areas, but more leaning toward" per explicit direction. */
    private static ResourceLocation pickBiasedWord(RandomSource random) {
        List<ResourceLocation> pool = new ArrayList<>();
        WordRegistry.getAllWords().forEach((id, word) -> {
            if (word.discoveryMethod() == com.dragonspeech.word.DiscoveryMethod.ADMIN_GRANTED
                    || word.discoveryMethod() == com.dragonspeech.word.DiscoveryMethod.GUESSED
                    || word.discoveryMethod() == com.dragonspeech.word.DiscoveryMethod.DANGER_WORD) {
                return;
            }
            int weight = word.discoveryMethod() == com.dragonspeech.word.DiscoveryMethod.ELVEN_TRIAL ? 6 : 1;
            for (int i = 0; i < weight; i++) {
                pool.add(id);
            }
        });
        return pool.get(random.nextInt(pool.size()));
    }
}
