package com.dragonspeech.mob.casting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The words a single spellcasting mob instance knows. Deliberately its own
 * tiny value type rather than reusing PlayerVocabulary/VocabularyAccess -
 * those are built entirely around ServerPlayer-keyed attachments and
 * per-word discovery bookkeeping (KnownWordEntry: how/when it was learned,
 * favorited status...), none of which a mob needs. A mob is simply BORN
 * knowing its starting pool (see MobWordPools) and, later, can be TAUGHT
 * more words only through trading - there's no guessing/backlash/
 * prerequisite gating to track per word here, just "does it know this or
 * not."
 */
public final class MobVocabulary {

    private static final String KEY = "known_words";

    private final Set<ResourceLocation> knownWords = new LinkedHashSet<>();

    public void learn(ResourceLocation wordId) {
        knownWords.add(wordId);
    }

    public void learnAll(Iterable<ResourceLocation> wordIds) {
        for (ResourceLocation id : wordIds) {
            knownWords.add(id);
        }
    }

    public boolean knows(ResourceLocation wordId) {
        return knownWords.contains(wordId);
    }

    /** Backs the mind-duel "Remove Word" command against a mob defender - see CommandEffectRegistry's "sever_random_word". Returns whether it actually knew the word (nothing to remove otherwise). */
    public boolean unlearn(ResourceLocation wordId) {
        return knownWords.remove(wordId);
    }

    public int size() {
        return knownWords.size();
    }

    /** Read-only view - callers should go through learn()/learnAll() to mutate. */
    public Set<ResourceLocation> words() {
        return knownWords;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ResourceLocation id : knownWords) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(KEY, list);
        return tag;
    }

    public void load(CompoundTag tag) {
        knownWords.clear();
        if (!tag.contains(KEY)) {
            return;
        }
        ListTag list = tag.getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                knownWords.add(id);
            }
        }
    }
}
