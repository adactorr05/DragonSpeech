package com.dragonspeech.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * The mod's public event bus for cross-mod integration - this is the
 * standard, long-stable Fabric event pattern (createArrayBacked), so it's
 * about as low-risk API-wise as anything in this project.
 *
 * WORD_DISCOVERED is the first of these; SPELL_CAST and BACKLASH_TRIGGERED
 * (mentioned in the original design notes) are natural, cheap additions
 * once the guessing system (Phase 3) exists to actually produce backlash
 * events - no need to stub them out before there's anything to fire them.
 */
public final class DragonSpeechEvents {

    private DragonSpeechEvents() {}

    /**
     * Fired whenever a player learns a new word, by any discovery method
     * (loot, NPC, ancient text, guessing, admin grant). Intended hook for
     * cross-mod reactions - e.g. a dragon mod's dragons reacting when a
     * nearby player learns a Fire-domain word.
     */
    public static final Event<WordDiscoveredCallback> WORD_DISCOVERED = EventFactory.createArrayBacked(
        WordDiscoveredCallback.class,
        listeners -> (player, word, method) -> {
            for (WordDiscoveredCallback listener : listeners) {
                listener.onWordDiscovered(player, word, method);
            }
        }
    );
}
