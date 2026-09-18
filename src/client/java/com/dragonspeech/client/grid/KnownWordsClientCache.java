package com.dragonspeech.client.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of the player's known words, kept fresh by
 * KnownWordsSyncPayload. Rendering-only - the real check always happens
 * server-side in CastRequestHandler, so nothing bad happens if this cache
 * is stale, empty, or (in a modified client) tampered with; it can only
 * ever affect what that one client sees, never what actually gets cast.
 */
public final class KnownWordsClientCache {

    private static List<ClientWordEntry> knownWords = new ArrayList<>();

    private KnownWordsClientCache() {}

    public record ClientWordEntry(
        String id,
        String trueName,
        String meaning,
        String category,
        String domain,
        boolean favorited,
        String discoveryMethod,
        float precision,
        String effectHandler
    ) {
        /**
         * True for any word registered as one of the enchantment
         * system's own effect handlers - "gala" (no effect_handler,
         * checked by name specifically since it's not castable), or any
         * word whose effect_handler starts with "apply_" (the naming
         * convention every enchantment handler in this mod actually
         * uses - ApplyWardEnchantEffectHandler, ApplyBlessingOrCurseEffectHandler,
         * ApplyVanillaEnchantEffectHandler, ApplyHidingCurseEffectHandler
         * all register under ids starting "apply_"). Deliberately NOT a
         * hardcoded word-id list and NOT a new word-JSON field - this
         * derives the answer from data already being synced for other
         * reasons, so it can't drift out of sync with what
         * EffectHandlerRegistry actually registers.
         */
        public boolean isEnchantmentWord() {
            return trueName.equalsIgnoreCase("gala") || (effectHandler != null && effectHandler.contains("apply_"));
        }
    }

    public static void update(List<ClientWordEntry> words) {
        knownWords = words;
    }

    public static List<ClientWordEntry> get() {
        return knownWords;
    }
}
