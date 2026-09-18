package com.dragonspeech.word;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Turns a candidate true-name string into a comparable hash, and checks
 * guesses against a stored hash. This must be the ONLY way a guess is
 * ever verified - never store or transmit a word's plaintext true name
 * to a client that has not yet discovered that word.
 *
 * IMPORTANT ARCHITECTURAL NOTE:
 * Hashing here protects against two real things: (1) a modified/cheating
 * client being able to self-validate a guess locally instead of asking
 * the server, and (2) a casual player opening the mod jar with a zip tool
 * and reading a plain list of every answer. It does NOT make the words
 * theoretically uncrackable by someone determined to dictionary-attack
 * the hashes offline - no client-shipped content can ever be made fully
 * secret, since the jar runs on the player's own machine. That's a normal,
 * accepted tradeoff shared by every game with in-game ciphers; a wiki
 * eventually appearing from someone's cracking effort isn't a bug. The
 * goal here is "no easy shortcuts," not "cryptographically unbreakable."
 */
public final class WordHashing {

    // This salt does not need to be secret - it only prevents rainbow
    // tables built for other projects/games from working against ours.
    private static final String SALT = "dragonspeech-ancient-language-v1";

    private WordHashing() {}

    public static String hash(String candidateTrueName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                (SALT + normalize(candidateTrueName)).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM; this should be unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean matches(String candidateTrueName, String hashedTrueName) {
        return hash(candidateTrueName).equalsIgnoreCase(hashedTrueName);
    }

    /**
     * Normalizes input before hashing so that stray whitespace or case
     * differences from the rune-keyboard input don't cause an otherwise
     * correct guess to fail. Adjust this if your rune input method needs
     * different normalization (e.g. stripping diacritics).
     */
    private static String normalize(String input) {
        return input.trim().toLowerCase();
    }

    /**
     * Standalone hash generator for content authors writing new word JSON
     * files - this has no Minecraft dependencies, so it can be run directly:
     *   javac WordHashing.java && java com.dragonspeech.word.WordHashing narro
     * (or just paste this class into any plain Java scratch file/IDE runner).
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java WordHashing <true_name> [<true_name> ...]");
            return;
        }
        for (String word : args) {
            System.out.println(word + " -> " + hash(word));
        }
    }
}

