package com.dragonspeech.grid;

import com.dragonspeech.word.WordCategory;

/**
 * The casting grid's fixed slot layout. Each slot only accepts words of
 * its declared category - this is what makes the grid physically enforce
 * basic grammar (you can't put a modifier in the verb slot) without
 * needing separate validation code for that part.
 *
 * This is a deliberately bounded, practical UI size - it does NOT
 * contradict "spells are basically infinite, limited only by imagination."
 * The grid is one convenient way to build a spell; typed/guessed spells
 * (Phase 3, including chat-casting) are unbounded in length the way the
 * design always intended. Nothing stops a later "expand the grid" feature
 * either, if playtesting shows 9 slots is too cramped.
 */
public enum GridSlot {
    VERB(WordCategory.VERB, "Verb"),
    TARGET_1(WordCategory.NOUN_TARGET, "Target"),
    TARGET_2(WordCategory.NOUN_TARGET, "Target"),
    MODIFIER_1(WordCategory.MODIFIER, "Modifier"),
    MODIFIER_2(WordCategory.MODIFIER, "Modifier"),
    MODIFIER_3(WordCategory.MODIFIER, "Modifier"),
    SCOPE(WordCategory.SCOPE, "Scope"),
    BINDING(WordCategory.BINDING, "Binding"),
    CONTROL(WordCategory.CONTROL, "Control");

    private final WordCategory category;
    private final String label;

    GridSlot(WordCategory category, String label) {
        this.category = category;
        this.label = label;
    }

    public WordCategory category() {
        return category;
    }

    public String label() {
        return label;
    }
}
