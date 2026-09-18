package com.dragonspeech.grid;

import com.dragonspeech.spell.SpellComposition;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What's currently placed in each grid slot, as word IDs (not resolved
 * Word objects). This is deliberately what gets sent over the network -
 * resolving IDs to real Word data, and re-checking the player actually
 * knows them, always happens server-side (see CastRequestHandler). Never
 * trust a client's claim about what it has placed.
 */
public record CastingGridState(Map<GridSlot, ResourceLocation> assignments) {

    public static CastingGridState empty() {
        return new CastingGridState(new EnumMap<>(GridSlot.class));
    }

    public CastingGridState withSlot(GridSlot slot, ResourceLocation wordId) {
        Map<GridSlot, ResourceLocation> copy = new EnumMap<>(assignments);
        copy.put(slot, wordId);
        return new CastingGridState(copy);
    }

    public CastingGridState withSlotCleared(GridSlot slot) {
        Map<GridSlot, ResourceLocation> copy = new EnumMap<>(assignments);
        copy.remove(slot);
        return new CastingGridState(copy);
    }

    public boolean knows(GridSlot slot) {
        return assignments.containsKey(slot);
    }

    /** True when the player placed only the stop-word (no verb) - this means "interrupt whatever I'm channeling," not "cast a new spell." */
    public boolean isControlOnlyRequest() {
        return assignments.containsKey(GridSlot.CONTROL) && !assignments.containsKey(GridSlot.VERB);
    }

    /** Resolves every placed word ID against the live WordRegistry, silently skipping anything that no longer resolves. */
    public SpellComposition toComposition() {
        List<Word> words = new ArrayList<>();
        for (ResourceLocation id : assignments.values()) {
            Word word = WordRegistry.get(id);
            if (word != null) {
                words.add(word);
            }
        }
        return new SpellComposition(words);
    }
}
