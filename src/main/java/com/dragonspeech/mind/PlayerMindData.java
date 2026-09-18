package com.dragonspeech.mind;

import com.dragonspeech.word.Domain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single attachment holding everything Phase 6 needs to persist about a
 * player between sessions:
 *
 * - ownName: their true name, generated lazily (TrueNameService).
 * - lastDominantDomain: which Domain had the player's highest Attunement
 *   the last time this was checked - see PersonalityShiftService, the
 *   hook that regenerates a true name when this changes ("a specific
 *   aspect of the player's personality is changed").
 * - learnedNames: every OTHER entity's true name this player has learned.
 * - trueNameProgress: the permanent Grimoire "true name card" list - one
 *   entry per entity this player has WON a mind duel against, tracking
 *   letters collected so far toward assembling that entity's name. See
 *   TrueNameProgress's own doc for why this is separate from
 *   learnedNames rather than folded into it - this tracks the IN-PROGRESS
 *   guessing game itself; learnedNames is the "do I actually, canonically
 *   know this name" record every other system (chat-hook instant
 *   connect, etc.) already reads.
 */
public record PlayerMindData(
    Optional<TrueName> ownName,
    Optional<Domain> lastDominantDomain,
    Map<UUID, LearnedTrueName> learnedNames,
    Map<UUID, TrueNameProgress> trueNameProgress
) {

    public static final Codec<PlayerMindData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        TrueName.CODEC.optionalFieldOf("own_name").forGetter(PlayerMindData::ownName),
        Domain.CODEC.optionalFieldOf("last_dominant_domain").forGetter(PlayerMindData::lastDominantDomain),
        Codec.unboundedMap(UUIDUtil.CODEC, LearnedTrueName.CODEC).optionalFieldOf("learned_names", Map.of()).forGetter(PlayerMindData::learnedNames),
        Codec.unboundedMap(UUIDUtil.CODEC, TrueNameProgress.CODEC).optionalFieldOf("true_name_progress", Map.of()).forGetter(PlayerMindData::trueNameProgress)
    ).apply(instance, PlayerMindData::new));

    public static PlayerMindData empty() {
        return new PlayerMindData(Optional.empty(), Optional.empty(), Map.of(), Map.of());
    }

    public PlayerMindData withOwnName(TrueName name) {
        return new PlayerMindData(Optional.of(name), lastDominantDomain, learnedNames, trueNameProgress);
    }

    public PlayerMindData withLastDominantDomain(Domain domain) {
        return new PlayerMindData(ownName, Optional.of(domain), learnedNames, trueNameProgress);
    }

    public PlayerMindData withLearned(LearnedTrueName learned) {
        Map<UUID, LearnedTrueName> copy = new HashMap<>(learnedNames);
        copy.put(learned.targetId(), learned);
        return new PlayerMindData(ownName, lastDominantDomain, Map.copyOf(copy), trueNameProgress);
    }

    public PlayerMindData withTrueNameProgress(TrueNameProgress progress) {
        Map<UUID, TrueNameProgress> copy = new HashMap<>(trueNameProgress);
        copy.put(progress.targetId(), progress);
        return new PlayerMindData(ownName, lastDominantDomain, learnedNames, Map.copyOf(copy));
    }

    public Optional<LearnedTrueName> learnedAbout(UUID targetId) {
        return Optional.ofNullable(learnedNames.get(targetId));
    }

    public Optional<TrueNameProgress> progressFor(UUID targetId) {
        return Optional.ofNullable(trueNameProgress.get(targetId));
    }
}
