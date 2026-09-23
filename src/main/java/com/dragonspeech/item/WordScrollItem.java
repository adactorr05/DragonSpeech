package com.dragonspeech.item;

import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.vocabulary.VocabularyService;
import com.dragonspeech.word.DiscoveryMethod;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * What a mob's word-trade actually hands over (see MobTradeOffers /
 * SpellcastingMobEntity's Merchant implementation) - a scroll bearing one
 * specific word, assigned at the moment the offer is generated rather than
 * on first use (unlike ScholarsFragmentItem, which rolls its word lazily on
 * first reading; here the word is exactly what the player paid emeralds to
 * see and buy, so it has to already be fixed on the stack before the trade
 * even completes).
 *
 * DELIBERATELY LOOKS LIKE UNIDENTIFIED PAPER now, not a spoiler-named
 * "Scroll of X" - per explicit direction: MobTradeOffers.buildOffer no
 * longer stamps the true word name onto the offer's display name, and
 * appendHoverText below shows only "An unknown word..." plus (if set)
 * which domain it belongs to (see DragonSpeechComponents.
 * WORD_CATEGORY_HINT) - "mind, element, force, etc." per direction,
 * without spoiling which SPECIFIC word it is. The model was also given a
 * real paper texture (word_scroll.json previously didn't exist at all -
 * a genuine missing-model bug, visible as the purple/black checkerboard
 * in your very first build log this session).
 *
 * Right-clicking teaches the stored word via VocabularyService - the same
 * path/DiscoveryMethod.MENTOR_NPC entry point every "found via mentor npc"
 * word in DICTIONARY.md already expects. Consumed on a successful or
 * already-known outcome; kept (not consumed) if the player isn't ready yet
 * (prerequisite word missing), so they can hang onto it and use it once
 * they've caught up rather than losing it for bad timing.
 */
public class WordScrollItem extends Item {

    public WordScrollItem(Properties properties) {
        super(properties);
    }

    /**
     * VERSION-RISK NOTE: appendHoverText's exact parameter list changed
     * in the 1.20.5 item-data rework (Level -> Item.TooltipContext).
     * Written from memory of that current shape - if this doesn't
     * compile, check Item.appendHoverText's exact signature in your
     * decompiled sources first.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("An unknown word of the Ancient Language").withStyle(ChatFormatting.GRAY));
        String domain = stack.get(DragonSpeechComponents.WORD_CATEGORY_HINT);
        if (domain != null) {
            String pretty = domain.substring(0, 1).toUpperCase(Locale.ROOT) + domain.substring(1).toLowerCase(Locale.ROOT);
            tooltip.add(Component.literal("Domain: " + pretty).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("Read to learn its true meaning.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        String wordIdRaw = stack.get(DragonSpeechComponents.TAUGHT_WORD);
        ResourceLocation wordId = wordIdRaw != null ? ResourceLocation.tryParse(wordIdRaw) : null;
        if (wordId == null) {
            serverPlayer.sendSystemMessage(Component.literal("This scroll's ink has faded to nothing."));
            return InteractionResultHolder.fail(stack);
        }

        Word word = WordRegistry.get(wordId);
        DiscoveryMethod source = word != null && word.discoveryMethod() == DiscoveryMethod.DANGER_WORD ? DiscoveryMethod.DANGER_WORD : DiscoveryMethod.MENTOR_NPC;
        VocabularyService.LearnResult result = VocabularyService.learnWord(serverPlayer, wordId, source);

        return switch (result) {
            case LEARNED -> {
                serverPlayer.sendSystemMessage(Component.literal(
                    "You have learned \"" + (word != null ? word.trueName() : wordId) + "\"" +
                        (word != null ? " - " + word.meaning() : "") + "."));
                stack.shrink(1);
                yield InteractionResultHolder.success(stack);
            }
            case ALREADY_KNOWN -> {
                serverPlayer.sendSystemMessage(Component.literal("You already know this word."));
                stack.shrink(1);
                yield InteractionResultHolder.success(stack);
            }
            case PREREQUISITES_NOT_MET -> {
                serverPlayer.sendSystemMessage(Component.literal(
                    "You are not ready to grasp this word yet - its simpler form must be learned first."));
                yield InteractionResultHolder.fail(stack);
            }
            case UNKNOWN_WORD -> {
                serverPlayer.sendSystemMessage(Component.literal("This scroll's ink has faded to nothing."));
                yield InteractionResultHolder.fail(stack);
            }
        };
    }
}
