package com.dragonspeech.item;

import com.dragonspeech.word.DiscoveryMethod;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a tablet's fixed content on first study: a flowing passage of
 * pseudo-language (built from the same phonetic rules as the real
 * vocabulary - see the style guide doc), interleaved with fragments of
 * half-readable lore, with 1-3 REAL words embedded among the noise. Each
 * embedded word carries a set of decoy meanings for the translation
 * minigame, generated here so the choices are fixed per-tablet.
 *
 * Content JSON shape:
 * {
 *   "tier": 1-3,
 *   "tokens": [ { "t": "text", "s": "style", "w": "word_id or absent" } ],
 *   "choices": { "<word_id>": ["meaning", "decoy", ...] },   // pre-shuffled
 *   "solved": [ "word_id", ... ]
 * }
 * Styles: clear, faded, crossed, reversed, obfuscated.
 */
public final class TabletContentGenerator {

    private static final String[] ONSETS = {"br", "dr", "fr", "gr", "kr", "thr", "st", "sk", "sl", "sn", "v", "h", "l", "m", "n", "k", "t", "d", "f"};
    private static final String[] VOWELS = {"a", "e", "i", "o", "u", "y", "au", "ei"};
    private static final String[] CODAS = {"r", "n", "m", "l", "s", "t", "k", "d", "th", "ll", "nn", "rn", "st", ""};

    private static final String[][] LORE_FRAGMENTS = {
        { "...before the first speaking...", "...the grey ones gave names to...", "...and fire answered, for it was named truly..." },
        { "...bound by oath in the old tongue...", "...none may lie in the language of making...", "...the wards held while the words were remembered..." },
        { "...the name beneath all names...", "...they who forgot their own true names...", "...what is named may be commanded, and what commands may be named..." }
    };

    private TabletContentGenerator() {}

    public static String generate(int tier, Random random) {
        JsonObject root = new JsonObject();
        root.addProperty("tier", tier);

        List<ResourceLocation> embedded = pickEmbeddedWords(tier, random);

        JsonArray tokens = new JsonArray();
        int totalTokens = 26 + random.nextInt(12);
        List<Integer> wordPositions = new ArrayList<>();
        while (wordPositions.size() < embedded.size()) {
            int pos = 3 + random.nextInt(totalTokens - 6);
            if (!wordPositions.contains(pos)) {
                wordPositions.add(pos);
            }
        }

        int lorePosition = 1 + random.nextInt(4); // one half-readable lore line near the start
        String[] loreSet = LORE_FRAGMENTS[tier - 1];
        String lore = loreSet[random.nextInt(loreSet.length)];

        for (int i = 0; i < totalTokens; i++) {
            JsonObject token = new JsonObject();
            int embeddedIndex = wordPositions.indexOf(i);

            if (embeddedIndex >= 0) {
                ResourceLocation wordId = embedded.get(embeddedIndex);
                Word word = WordRegistry.get(wordId);
                token.addProperty("t", word.trueName());
                token.addProperty("s", "clear"); // significant words are always legible - finding them is the puzzle, not squinting at them
                token.addProperty("w", wordId.toString());
            } else if (i == lorePosition) {
                token.addProperty("t", lore);
                token.addProperty("s", "faded");
            } else {
                token.addProperty("t", pseudoWord(random));
                token.addProperty("s", pickDistortion(tier, random));
            }
            tokens.add(token);
        }
        root.add("tokens", tokens);

        JsonObject choices = new JsonObject();
        int decoyCount = switch (tier) { case 1 -> 2; case 2 -> 3; default -> 4; };
        for (ResourceLocation wordId : embedded) {
            choices.add(wordId.toString(), buildChoices(wordId, decoyCount, random));
        }
        root.add("choices", choices);
        root.add("solved", new JsonArray());

        return root.toString();
    }

    private static List<ResourceLocation> pickEmbeddedWords(int tier, Random random) {
        int count = switch (tier) {
            case 1 -> random.nextFloat() < 0.7f ? 1 : 2;
            case 2 -> {
                float roll = random.nextFloat();
                yield roll < 0.4f ? 1 : (roll < 0.8f ? 2 : 3);
            }
            default -> random.nextFloat() < 0.5f ? 2 : 3;
        };

        List<ResourceLocation> pool = new ArrayList<>();
        WordRegistry.getAllWords().forEach((id, word) -> {
            int weight = weightFor(word.discoveryMethod(), tier);
            for (int i = 0; i < weight; i++) {
                pool.add(id);
            }
        });

        List<ResourceLocation> picked = new ArrayList<>();
        while (picked.size() < count && !pool.isEmpty()) {
            ResourceLocation candidate = pool.get(random.nextInt(pool.size()));
            if (!picked.contains(candidate)) {
                picked.add(candidate);
            }
            pool.removeIf(candidate::equals);
        }
        return picked;
    }

    /** Deeper tiers carry deeper words - a primordial tablet is where elven-trial vocabulary actually lives in the wild. */
    private static int weightFor(DiscoveryMethod method, int tier) {
        return switch (method) {
            case RUIN_TABLET -> tier == 1 ? 6 : (tier == 2 ? 3 : 1);
            case ANCIENT_TEXT -> tier == 1 ? 3 : (tier == 2 ? 5 : 3);
            case MENTOR_NPC -> 2;
            case ELVEN_TRIAL -> tier == 1 ? 0 : (tier == 2 ? 2 : 6);
            case ADMIN_GRANTED, GUESSED -> 0;
        };
    }

    private static JsonArray buildChoices(ResourceLocation wordId, int decoyCount, Random random) {
        Word word = WordRegistry.get(wordId);
        List<String> meanings = new ArrayList<>();
        meanings.add(word.meaning());

        List<Word> others = new ArrayList<>(WordRegistry.getAllWords().values());
        others.removeIf(other -> other.meaning().equals(word.meaning()));
        java.util.Collections.shuffle(others, random);
        for (int i = 0; i < Math.min(decoyCount, others.size()); i++) {
            meanings.add(others.get(i).meaning());
        }

        java.util.Collections.shuffle(meanings, random);
        JsonArray array = new JsonArray();
        meanings.forEach(array::add);
        return array;
    }

    private static String pseudoWord(Random random) {
        StringBuilder word = new StringBuilder();
        int syllables = 1 + random.nextInt(3);
        for (int i = 0; i < syllables; i++) {
            word.append(ONSETS[random.nextInt(ONSETS.length)]);
            word.append(VOWELS[random.nextInt(VOWELS.length)]);
            if (random.nextFloat() < 0.6f) {
                word.append(CODAS[random.nextInt(CODAS.length)]);
            }
        }
        return word.toString();
    }

    /** Higher tiers are harder to read - more of the surrounding text is damaged. */
    private static String pickDistortion(int tier, Random random) {
        float damage = switch (tier) { case 1 -> 0.35f; case 2 -> 0.6f; default -> 0.8f; };
        if (random.nextFloat() > damage) {
            return "clear";
        }
        return switch (random.nextInt(4)) {
            case 0 -> "faded";
            case 1 -> "crossed";
            case 2 -> "reversed";
            default -> "obfuscated";
        };
    }
}
