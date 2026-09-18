package com.dragonspeech.network;

import com.dragonspeech.guess.BacklashResolver;
import com.dragonspeech.item.WordTabletItem;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.vocabulary.VocabularyService;
import com.dragonspeech.word.DiscoveryMethod;
import com.dragonspeech.word.RiskTier;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves a translation attempt against the tablet the player is
 * actually HOLDING - everything from the client is untrusted, so the
 * word must genuinely be embedded in the held tablet's content, and
 * correctness is checked against the live registry meaning, never
 * against anything the client sent.
 *
 * Correct: the word is learned (prerequisites still apply - a correct
 * translation you aren't ready for stays unsolved, retryable later) and
 * marked solved on the tablet. When every embedded word is solved, the
 * tablet crumbles.
 *
 * Wrong: backlash scaled to the tablet's tier - misreading a primordial
 * inscription is a genuinely dangerous act.
 */
public final class TranslateAttemptHandler {

    private TranslateAttemptHandler() {}

    public static void handle(ServerPlayer player, String wordIdRaw, String chosenMeaning) {
        ItemStack held = player.getMainHandItem();
        WordTabletItem tablet = asTablet(held);

        if (tablet == null) {
            held = player.getOffhandItem();
            tablet = asTablet(held);
        }

        if (tablet == null) {
            player.sendSystemMessage(Component.literal("You must be holding the tablet to work at its translation."));
            return;
        }

        String contentJson = held.get(DragonSpeechComponents.TABLET_CONTENT);
        if (contentJson == null) {
            return;
        }

        try {
            JsonObject content = JsonParser.parseString(contentJson).getAsJsonObject();
            JsonObject choices = content.getAsJsonObject("choices");
            if (choices == null || !choices.has(wordIdRaw)) {
                return; // word isn't actually in this tablet - stale/tampered client
            }

            JsonArray solved = content.getAsJsonArray("solved");
            for (JsonElement element : solved) {
                if (element.getAsString().equals(wordIdRaw)) {
                    player.sendSystemMessage(Component.literal("That passage already lies open to you."));
                    return;
                }
            }

            ResourceLocation wordId = ResourceLocation.parse(wordIdRaw);
            Word word = WordRegistry.get(wordId);
            if (word == null) {
                return;
            }

            if (!word.meaning().equals(chosenMeaning)) {
                RiskTier tier = tablet.tier() >= 3 ? RiskTier.SEVERE
                    : (tablet.tier() == 2 ? RiskTier.MODERATE : RiskTier.TRIVIAL);
                BacklashResolver.applyBacklash(player, tier, 0.75f);
                player.sendSystemMessage(Component.literal("The meaning twists away from you, and the old tongue exacts its price."));
                return;
            }

            VocabularyService.LearnResult result = VocabularyService.learnWord(player, wordId, DiscoveryMethod.RUIN_TABLET);
            switch (result) {
                case LEARNED, ALREADY_KNOWN -> {
                    solved.add(wordIdRaw);
                    held.set(DragonSpeechComponents.TABLET_CONTENT, content.toString());
                    player.sendSystemMessage(Component.literal(
                        "The inscription yields: \"" + word.trueName() + "\" - " + word.meaning()));

                    if (solved.size() >= choices.size()) {
                        held.shrink(1);
                        player.sendSystemMessage(Component.literal("Its purpose spent, the tablet crumbles to dust."));
                    } else {
                        // Refresh the open reading screen so the solved word turns green in place.
                        DragonSpeechNetworking.sendOpenTablet(player, content.toString());
                    }
                }
                case PREREQUISITES_NOT_MET -> player.sendSystemMessage(Component.literal(
                    "You grasp its meaning, but it rests on simpler words you have not yet learned. The tablet will wait."));
                case UNKNOWN_WORD -> { /* defensive - registry changed under us */ }
            }
        } catch (Exception e) {
            // Malformed content/attempt - fail silently rather than crash a tick.
        }
    }

    private static WordTabletItem asTablet(ItemStack stack) {
        return stack.getItem() instanceof WordTabletItem tablet ? tablet : null;
    }
}
