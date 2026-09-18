package com.dragonspeech.guess;

import net.minecraft.resources.ResourceLocation;

public record GuessOutcome(Status status, String message, ResourceLocation wordId) {

    public enum Status { LEARNED, ALREADY_KNOWN, PREREQUISITES_NOT_MET, MISS }

    public static GuessOutcome learned(ResourceLocation id, String message) {
        return new GuessOutcome(Status.LEARNED, message, id);
    }

    public static GuessOutcome alreadyKnown(ResourceLocation id) {
        return new GuessOutcome(Status.ALREADY_KNOWN, "You already know this word.", id);
    }

    public static GuessOutcome prerequisitesNotMet(ResourceLocation id) {
        return new GuessOutcome(Status.PREREQUISITES_NOT_MET,
            "You sense its shape, but this word rests on simpler words you have not yet learned. Seek those first.", id);
    }

    public static GuessOutcome miss(String message) {
        return new GuessOutcome(Status.MISS, message, null);
    }
}
