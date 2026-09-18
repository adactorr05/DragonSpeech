package com.dragonspeech.mind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A player's true name in the Ancient Language: their "name in speech",
 * per the dictionary's "nafn" entry - the single highest-risk, highest-
 * precision noun in the whole vocabulary (0.95 precision, CATASTROPHIC
 * guess-risk) for good reason.
 *
 * plaintext is stored ONLY so the owning player can eventually be told
 * their own name (e.g. a future ritual/quest reveal) and so
 * TrueNameService can regenerate it deterministically-but-differently
 * when personality changes. hashedPlaintext is the ONLY thing ever
 * compared against when an attacker claims to know it - see
 * TrueNameService.matches(), mirroring WordHashing's rule exactly.
 *
 * generation increments every time the name changes (per design notes:
 * "For true names, they can be changed if a specific aspect of the
 * player's personality is changed"). This isn't wired to a personality
 * system yet - TrueNameService.regenerate() is the hook point for
 * whatever drives that later - but storing the counter now means an
 * attacker who learned an OLD true name can be told it no longer works,
 * instead of silently keeping power they should have lost.
 */
public record TrueName(String plaintext, String hashedPlaintext, int generation) {

    public static final Codec<TrueName> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("plaintext").forGetter(TrueName::plaintext),
        Codec.STRING.fieldOf("hashed_plaintext").forGetter(TrueName::hashedPlaintext),
        Codec.INT.optionalFieldOf("generation", 0).forGetter(TrueName::generation)
    ).apply(instance, TrueName::new));
}
