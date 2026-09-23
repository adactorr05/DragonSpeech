package com.dragonspeech.guess;

import com.dragonspeech.vocabulary.VocabularyService;
import com.dragonspeech.word.DiscoveryMethod;
import com.dragonspeech.word.RiskTier;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Resolves a raw typed guess into an outcome. Correctness is ALWAYS
 * decided by Word.matchesGuess() (a hash comparison) - never by comparing
 * plaintext directly, so this stays consistent with WordHashing's whole
 * reason for existing. Plaintext trueName is only ever touched here to
 * gauge how CLOSE a wrong guess was (for scaling backlash), which is safe
 * precisely because that comparison happens entirely server-side and its
 * result is never revealed to the player - they're told "close" or "not
 * close," never which word they were close to.
 *
 * NOTE: there's no rate limiting here yet. A scripted client could spam
 * guesses far faster than a human typing - worth adding a per-player
 * guess cooldown (a few hundred ms) as a follow-up hardening pass, not
 * required for the system to work correctly today.
 */
public final class GuessResolver {

    private static final int NEAR_MISS_THRESHOLD = 2;

    private GuessResolver() {}

    public static GuessOutcome resolve(ServerPlayer player, String rawInput) {
        String candidate = normalize(rawInput);
        if (candidate.isEmpty()) {
            return GuessOutcome.miss("You speak, but no word takes shape.");
        }

        if (com.dragonspeech.wow.WowPhraseState.matches(player.getServer(), candidate)) {
            if (com.dragonspeech.wow.WordOfWordsKnowledge.knows(player)) {
                return GuessOutcome.alreadyKnown(com.dragonspeech.wow.WordOfWordsKnowledge.WORD_ID);
            }
            com.dragonspeech.wow.WordOfWordsKnowledge.learn(player, DiscoveryMethod.GUESSED);
            return GuessOutcome.learned(com.dragonspeech.wow.WordOfWordsKnowledge.WORD_ID,
                "The impossible word settles into your mind: \"" + com.dragonspeech.wow.WowPhraseState.current(player.getServer())
                    + "\" - the Word of Words, the Ancient Language's authority over magic itself.");
        }

        for (Map.Entry<ResourceLocation, Word> entry : WordRegistry.getAllWords().entrySet()) {
            if (entry.getValue().matchesGuess(candidate)) {
                return resolveExactMatch(player, entry.getKey(), entry.getValue());
            }
        }

        return resolveMiss(player, candidate);
    }

    private static GuessOutcome resolveExactMatch(ServerPlayer player, ResourceLocation id, Word word) {
        if (word.discoveryMethod() == DiscoveryMethod.DANGER_WORD) {
            BacklashResolver.applyBacklash(player, RiskTier.CATASTROPHIC, 1.0f);
            return GuessOutcome.miss("The word answers violently, then tears itself from your grasp. Some names refuse to be stolen by guessing.");
        }
        VocabularyService.LearnResult result = VocabularyService.learnWord(player, id, DiscoveryMethod.GUESSED);

        return switch (result) {
            case LEARNED -> GuessOutcome.learned(id,
                "The true name comes to you: \"" + word.trueName() + "\" - " + word.meaning());
            case ALREADY_KNOWN -> GuessOutcome.alreadyKnown(id);
            case PREREQUISITES_NOT_MET -> GuessOutcome.prerequisitesNotMet(id);
            case UNKNOWN_WORD -> GuessOutcome.miss("Something is wrong - that word could not be found."); // defensive; shouldn't happen
        };
    }

    private static GuessOutcome resolveMiss(ServerPlayer player, String candidate) {
        Word closest = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Word word : WordRegistry.getAllWords().values()) {
            if (word.discoveryMethod() == DiscoveryMethod.DANGER_WORD) continue;
            int distance = levenshtein(candidate, normalize(word.trueName()));
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = word;
            }
        }

        boolean nearMiss = closest != null && bestDistance <= NEAR_MISS_THRESHOLD;
        RiskTier tier = nearMiss ? closest.riskTier() : RiskTier.TRIVIAL;
        float severityScale = nearMiss ? 0.5f : 1.0f;

        // Blessing of the Steady Hand ("handheill") - -15% backlash severity per level (up to 3 levels = -45%), worn only.
        int steadyHandLevel = com.dragonspeech.enchant.EquippedEnchantments.levelOf(player, "handheill");
        severityScale *= Math.max(0.1f, 1f - 0.15f * steadyHandLevel);

        BacklashResolver.applyBacklash(player, tier, severityScale);

        String message = nearMiss
            ? "You feel you were close, but something is wrong, and the word resists you."
            : "The word slips away, meaningless - nothing answers.";

        return GuessOutcome.miss(message);
    }

    private static String normalize(String input) {
        return input.trim().toLowerCase();
    }

    /** Standard Levenshtein edit distance - used only to gauge how close a wrong guess was, never for the actual correctness check (that's always the hash). */
    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
