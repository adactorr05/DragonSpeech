package com.dragonspeech.enchant;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The actual item-data-component payload - a flat list, since an item can hold multiple different wards at once alongside any number of blessings/curses (all confirmed explicitly - multiple wards allowed, multiple blessings/curses allowed). */
public record MagicEnchantments(List<MagicEnchantment> entries) {

    public static final MagicEnchantments EMPTY = new MagicEnchantments(List.of());

    public static final Codec<MagicEnchantments> CODEC = MagicEnchantment.CODEC.listOf()
        .xmap(MagicEnchantments::new, MagicEnchantments::entries);

    public MagicEnchantments with(MagicEnchantment added) {
        List<MagicEnchantment> next = new ArrayList<>(entries);
        next.add(added);
        return new MagicEnchantments(next);
    }

    public MagicEnchantments without(String wordId) {
        List<MagicEnchantment> next = entries.stream().filter(e -> !e.wordId().equals(wordId)).toList();
        return new MagicEnchantments(next);
    }

    public MagicEnchantments replacing(String wordId, MagicEnchantment replacement) {
        List<MagicEnchantment> next = new ArrayList<>();
        for (MagicEnchantment e : entries) {
            next.add(e.wordId().equals(wordId) ? replacement : e);
        }
        return new MagicEnchantments(next);
    }

    public Optional<MagicEnchantment> find(String wordId) {
        return entries.stream().filter(e -> e.wordId().equals(wordId)).findFirst();
    }

    public boolean has(String wordId) {
        return find(wordId).isPresent();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
