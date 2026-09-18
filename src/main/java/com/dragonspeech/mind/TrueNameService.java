package com.dragonspeech.mind;

import com.dragonspeech.word.WordHashing;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/**
 * Generates and checks true names. Hashing reuses WordHashing verbatim -
 * a true name IS a word (see the dictionary's "nafn" entry), it just
 * happens to be one generated per-player instead of authored in a
 * datapack, so it deserves exactly the same "never compare plaintext,
 * never ship plaintext to a client that hasn't earned it" discipline.
 *
 * Name generation is a simple syllable-shuffle over the Ancient
 * Language's own phoneme inventory (see docs/ANCIENT_LANGUAGE_STYLE_GUIDE.md)
 * so a generated true name always at least SOUNDS like it belongs to
 * this language, even though it will essentially never match a real
 * dictionary word. It is seeded from the player's UUID + generation
 * counter, so the SAME name always regenerates for the same player at
 * the same generation - useful for admin tooling/debugging - but a new
 * generation (see regenerate()) produces a genuinely different name.
 */
public final class TrueNameService {

    private static final String[] ONSETS = {"th", "kr", "sv", "fj", "br", "dr", "gl", "hr", "sk", "vr", "n", "m", "s", "v", "l", "r"};
    private static final String[] VOWELS = {"a", "e", "i", "o", "u", "ae", "ei", "ou"};
    private static final String[] CODAS = {"n", "r", "l", "th", "s", "d", "k", "nd", "rk", "lt", ""};

    private TrueNameService() {}

    /** Returns the player's existing true name, generating and persisting one on first call. Never regenerates an existing name - use regenerate() for that. */
    public static TrueName getOrCreate(ServerPlayer player) {
        PlayerMindData data = MindDataAccess.get(player);
        if (data.ownName().isPresent()) {
            return data.ownName().get();
        }
        TrueName generated = generate(player.getUUID(), 0);
        MindDataAccess.set(player, data.withOwnName(generated));
        return generated;
    }

    /**
     * Forces a new true name at the next generation. Per design notes,
     * this should be called when "a specific aspect of the player's
     * personality is changed" - no such trigger exists yet, so this is
     * currently only reachable via admin command, as the hook point for
     * whatever narrative/mechanical system eventually drives it.
     */
    public static TrueName regenerate(ServerPlayer player) {
        PlayerMindData data = MindDataAccess.get(player);
        int nextGeneration = data.ownName().map(TrueName::generation).orElse(-1) + 1;
        TrueName fresh = generate(player.getUUID(), nextGeneration);
        MindDataAccess.set(player, data.withOwnName(fresh));
        return fresh;
    }

    /** Records that `learner` now knows `target`'s CURRENT true name. Call this only from a resolved, successful deep-scan/forbidden-scan action - never speculatively. */
    public static void learn(ServerPlayer learner, ServerPlayer target) {
        learnForEntity(learner, target);
    }

    /** learn(), generalized to either a player or a mob target - a chat-spoken true name works the same way regardless of what kind of mind it belongs to. */
    public static void learnForEntity(ServerPlayer learner, net.minecraft.world.entity.LivingEntity target) {
        TrueName targetName = getOrCreateForEntity(target);
        LearnedTrueName learned = new LearnedTrueName(target.getUUID(), targetName.hashedPlaintext(), targetName.generation());
        MindDataAccess.set(learner, MindDataAccess.get(learner).withLearned(learned));
    }

    /** True if `guess` matches `target`'s CURRENT true name - the actual check behind the "type the name" prompt. Never compares plaintext directly; always through WordHashing, same discipline as every other guessable word in this mod. */
    public static boolean guessCorrect(ServerPlayer target, String guess) {
        if (guess == null || guess.isBlank()) {
            return false;
        }
        TrueName current = getOrCreate(target);
        return com.dragonspeech.word.WordHashing.matches(guess, current.hashedPlaintext());
    }

    /** True only if `learner` knows `target`'s CURRENT true name - a name learned before a regenerate() silently stops counting. */
    public static boolean knows(ServerPlayer learner, ServerPlayer target) {
        return knowsEntity(learner, target);
    }

    /** knows(), generalized to either a player or a mob target. */
    public static boolean knowsEntity(ServerPlayer learner, net.minecraft.world.entity.LivingEntity target) {
        TrueName current = getOrCreateForEntity(target);
        return MindDataAccess.get(learner).learnedAbout(target.getUUID())
            .map(l -> l.generationLearned() == current.generation() && l.hashedPlaintext().equals(current.hashedPlaintext()))
            .orElse(false);
    }

    /**
     * Mobs have true names too, per the current design - randomly
     * generated, same as a player's, but with nothing to persist: since
     * generate() is a pure function of (UUID, generation) and a mob's
     * UUID is stable for the entity's whole lifetime (Minecraft persists
     * entity UUIDs through chunk save/load same as anything else), just
     * always generating at generation 0 gives the exact same name every
     * time without needing any attachment/storage of its own. If mobs
     * ever need a "personality changed" regenerate() hook, that's the
     * point where this would need real storage - not needed yet.
     */
    public static TrueName getOrCreateForMob(net.minecraft.world.entity.LivingEntity mob) {
        return generate(mob.getUUID(), 0);
    }

    /** getOrCreate()/getOrCreateForMob() dispatch in one place, for callers (like the True Name guess flow) that don't already know whether the defender is a player or a mob. */
    public static TrueName getOrCreateForEntity(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof ServerPlayer player ? getOrCreate(player) : getOrCreateForMob(entity);
    }

    /** guessCorrect(), generalized to either a player or a mob defender. */
    public static boolean guessCorrectForEntity(net.minecraft.world.entity.LivingEntity entity, String guess) {
        if (guess == null || guess.isBlank()) {
            return false;
        }
        TrueName current = getOrCreateForEntity(entity);
        return com.dragonspeech.word.WordHashing.matches(guess, current.hashedPlaintext());
    }

    private static TrueName generate(java.util.UUID seedSource, int generation) {
        Random random = new Random(seedSource.getLeastSignificantBits() ^ seedSource.getMostSignificantBits() ^ (generation * 0x9E3779B9L));
        int syllables = 2 + random.nextInt(2); // 2-3 syllables, matches the dictionary's own word lengths
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < syllables; i++) {
            name.append(ONSETS[random.nextInt(ONSETS.length)]);
            name.append(VOWELS[random.nextInt(VOWELS.length)]);
            name.append(CODAS[random.nextInt(CODAS.length)]);
        }
        String plaintext = name.toString();
        return new TrueName(plaintext, WordHashing.hash(plaintext), generation);
    }
}
