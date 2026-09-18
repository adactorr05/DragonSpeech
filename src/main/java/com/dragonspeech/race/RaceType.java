package com.dragonspeech.race;

import com.dragonspeech.word.Domain;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Set;

/**
 * A player's origin - chosen once, persists for the character's whole
 * life (this attachment IS copyOnDeath, unlike wards/wounds). Each race
 * discounts casting in its affinity domains and carries one small,
 * flavorful trait. Deliberately a fixed enum, same "words/data select,
 * code defines the real set" boundary as everything else load-bearing
 * in this mod.
 */
public enum RaceType implements StringRepresentable {
    HUMAN(Set.of(), "Versatile and adaptable - no domain runs cheap for you, but attunement itself grows faster in every domain, mirroring how quickly humans in the old tales came to the language."),
    ELF(Set.of(Domain.MIND, Domain.LIFE), "Kin to the old tongue itself - Mind and Life domains cost less to speak in, and your thoughts resist intrusion more than most."),
    DWARF(Set.of(Domain.EARTH, Domain.FORCE), "Stone-kin - Earth and Force domains cost less, and your body shrugs off knockback and crushing harm more readily."),
    URGAL(Set.of(Domain.FORCE, Domain.DEATH), "Hard-born - Force and Death domains cost less, and you carry more raw health than the other kindreds.");

    public static final Codec<RaceType> CODEC = StringRepresentable.fromEnum(RaceType::values);

    private final Set<Domain> affinities;
    private final String description;

    RaceType(Set<Domain> affinities, String description) {
        this.affinities = affinities;
        this.description = description;
    }

    public Set<Domain> affinities() {
        return affinities;
    }

    public String description() {
        return description;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
