package com.dragonspeech.network;

import com.dragonspeech.guess.GuessResolver;
import com.dragonspeech.spell.SpellComposition;
import com.dragonspeech.vocabulary.VocabularyService;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Chat-casting: speak the words directly in chat and they cast - no GUI
 * at all. This is the fastest way to cast once you know your words, and
 * the most dangerous: the message must consist entirely of words you
 * actually know, spoken perfectly, or the spell fails with backlash.
 *
 * A message containing ANY Ancient Language word is treated as an
 * utterance in the language (you cannot casually speak it - very much in
 * the spirit of the source material) - it is hidden from other players
 * and replaced with scrambled gibberish of the same shape, so bystanders
 * can tell something was spoken without being able to copy it.
 *
 * Speaking a real word you have NOT yet learned routes through the same
 * GuessResolver as the guessing screen - speaking a true name aloud
 * correctly IS discovering it, prerequisites and backlash included. One
 * unknown token per message is processed, so a wild sentence can't
 * trigger a backlash barrage.
 *
 * VERSION-RISK NOTE: ServerMessageEvents.ALLOW_CHAT_MESSAGE and
 * PlayerChatMessage.signedContent() are this file's newly-touched API. If
 * signedContent() doesn't resolve, check PlayerChatMessage in your
 * decompiled sources for the method returning the raw message string.
 */
public final class ChatCastHooks {

    private ChatCastHooks() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String raw = message.signedContent();
            MinecraftServer server = sender.getServer();

            // The Word of Words is intentionally NOT a normal dictionary entry.
            // Check it before ordinary Ancient-Language detection so the world's
            // generated secret cannot leak through natural word systems or chat.
            if (server != null && com.dragonspeech.wow.WowPhraseState.matches(server, raw)) {
                server.execute(() -> {
                    boolean learned = com.dragonspeech.wow.WordOfWordsKnowledge.learn(sender, com.dragonspeech.word.DiscoveryMethod.GUESSED);
                    if (learned) {
                        sender.sendSystemMessage(Component.literal("The Word of Words fixes itself in your memory."));
                    }
                    com.dragonspeech.wow.WordOfWordsEffect.attempt(sender);
                });
                return false;
            }

            String[] tokens = raw.trim().toLowerCase(Locale.ROOT).split("\\s+");
            boolean anyAncientWord = false;
            for (String token : tokens) {
                if (findRegistryWord(token) != null || (server != null && com.dragonspeech.wow.WowPhraseState.matches(server, token))) {
                    anyAncientWord = true;
                    break;
                }
            }
            if (!anyAncientWord) {
                return true; // ordinary chat - let it through untouched
            }

            // An utterance in the Ancient Language: never shown verbatim.
            broadcastGibberish(sender, tokens);
            if (server != null) {
                server.execute(() -> processUtterance(sender, tokens));
            }
            return false; // suppress the original message
        });
    }

    private static void processUtterance(ServerPlayer speaker, String[] tokens) {
        // A dead player cannot cast at all - both self-revival forms
        // ("aftrlifga sjalfan" cast beforehand, alive) now resolve
        // automatically at the moment of death via DeathHooks, so there is
        // nothing left for a dead player to usefully speak.
        if (!speaker.isAlive()) {
            return;
        }

        List<Word> knownWords = new ArrayList<>();
        String firstUnknownToken = null;

        MinecraftServer server = speaker.getServer();
        // The Word of Words is the authority that CREATED these prohibitions.  A halt may
        // suppress ordinary Ancient-Language magic, but it cannot suppress the Word itself
        // or a sentence that explicitly invokes it.  This keeps the escape/rewrite route
        // available even from inside a Word-created anti-magic zone.
        boolean invokesWordOfWords = server != null && java.util.Arrays.stream(tokens)
            .anyMatch(token -> com.dragonspeech.wow.WowPhraseState.matches(server, token));
        if (!invokesWordOfWords && com.dragonspeech.wow.WordOfWordsHaltManager.isCastingSuppressed(speaker)) {
            speaker.sendSystemMessage(Component.literal("The words leave your mouth, but magic itself is halted here."));
            return;
        }

        for (String token : tokens) {
            if (server != null && com.dragonspeech.wow.WowPhraseState.matches(server, token)) {
                if (!com.dragonspeech.wow.WordOfWordsKnowledge.knows(speaker)) {
                    com.dragonspeech.wow.WordOfWordsKnowledge.learn(speaker, com.dragonspeech.word.DiscoveryMethod.GUESSED);
                    speaker.sendSystemMessage(Component.literal("The Word of Words fixes itself in your memory."));
                }
                knownWords.add(com.dragonspeech.wow.WordOfWordsKnowledge.dynamicWord(server));
                continue;
            }
            Word known = findKnownWord(speaker, token);
            if (known != null) {
                knownWords.add(known);
            } else if (firstUnknownToken == null) {
                firstUnknownToken = token;
            }
        }

        if (firstUnknownToken != null) {
            // Imperfect speech - the spell fails, and the failed word is
            // resolved exactly like a guess (may learn it, may backlash).
            speaker.sendSystemMessage(Component.literal("The words tangle on your tongue - the spell collapses."));
            var outcome = GuessResolver.resolve(speaker, firstUnknownToken);
            speaker.sendSystemMessage(Component.literal(outcome.message()));
            return;
        }

        CastRequestHandler.handleComposition(speaker, new SpellComposition(knownWords));
    }

    private static Word findRegistryWord(String token) {
        for (Word word : WordRegistry.getAllWords().values()) {
            if (word.trueName().equalsIgnoreCase(token)) {
                return word;
            }
        }
        return null;
    }

    private static Word findKnownWord(ServerPlayer player, String token) {
        for (Word word : VocabularyService.getKnownWords(player)) {
            if (word.trueName().equalsIgnoreCase(token)) {
                return word;
            }
        }
        return null;
    }

    /** Everyone but the speaker sees same-shaped gibberish; the speaker sees their real words. */
    private static void broadcastGibberish(ServerPlayer speaker, String[] tokens) {
        MinecraftServer server = speaker.getServer();
        if (server == null) {
            return;
        }

        Random random = new Random();
        StringBuilder gibberish = new StringBuilder();
        for (String token : tokens) {
            for (int i = 0; i < token.length(); i++) {
                gibberish.append((char) ('a' + random.nextInt(26)));
            }
            gibberish.append(' ');
        }

        String speakerName = speaker.getGameProfile().getName();
        Component garbled = Component.literal("<" + speakerName + "> " + gibberish.toString().trim());
        Component clear = Component.literal("You speak: " + String.join(" ", tokens));

        for (ServerPlayer listener : server.getPlayerList().getPlayers()) {
            if (listener.getUUID().equals(speaker.getUUID())) {
                listener.sendSystemMessage(clear);
            } else {
                listener.sendSystemMessage(garbled);
            }
        }
    }
}
