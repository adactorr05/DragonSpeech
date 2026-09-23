package com.dragonspeech.item;

import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A torn scrap of a scholar's translation notes: one word of the old
 * tongue and its meaning, side by side. The pairing is assigned on first
 * reading and permanent afterward - each fragment is a fixed dictionary
 * scrap, collectible and tradeable, never consumed.
 *
 * Fragments do NOT teach the word. They give the PLAYER the knowledge to
 * answer a tablet's translation choices correctly - the learning aid the
 * whole tablet minigame is balanced around.
 */
public class ScholarsFragmentItem extends Item {

    private static final Random RANDOM = new Random();

    public ScholarsFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        String wordId = stack.get(DragonSpeechComponents.FRAGMENT_WORD);
        if (wordId == null) {
            wordId = pickWord().toString();
            stack.set(DragonSpeechComponents.FRAGMENT_WORD, wordId);
        }

        Word word = WordRegistry.get(ResourceLocation.parse(wordId));
        if (word == null) {
            serverPlayer.sendSystemMessage(Component.literal("The ink has run beyond reading."));
            return InteractionResultHolder.fail(stack);
        }

        serverPlayer.sendSystemMessage(Component.literal(
            "The fragment reads: \"" + word.trueName() + "\" - " + word.meaning()));
        return InteractionResultHolder.success(stack);
    }

    /** Weighted toward common vocabulary - deep words rarely survive on scraps. */
    private static ResourceLocation pickWord() {
        List<ResourceLocation> pool = new ArrayList<>();
        WordRegistry.getAllWords().forEach((id, word) -> {
            int weight = switch (word.discoveryMethod()) {
                case RUIN_TABLET -> 5;
                case ANCIENT_TEXT -> 3;
                case MENTOR_NPC -> 2;
                case ELVEN_TRIAL -> 1;
                case DANGER_WORD, ADMIN_GRANTED, GUESSED -> 0;
            };
            for (int i = 0; i < weight; i++) {
                pool.add(id);
            }
        });
        return pool.get(RANDOM.nextInt(pool.size()));
    }
}
