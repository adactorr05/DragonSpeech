package com.dragonspeech.dragon.breed;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A single dragon breed's data - colors, growth, size, attribute
 * overrides, damage immunities, and hatching-habitat rules. Concept
 * and field set adapted from Dragon Mounts Legacy's own DragonBreed
 * (com.github.kay9.dragonmounts.dragon.DragonBreed, GPL-3.0,
 * https://github.com/TheRealKingslayer1/Dragon-Mounts-Legacy) - same
 * idea (a data-driven breed a dragon entity holds a reference to, not
 * a subclass per breed), but loaded the way THIS project already loads
 * its own data-driven content (see WordRegistry, same package-sibling
 * pattern: a Codec used inside a plain SimpleJsonResourceReloadListener)
 * rather than porting Dragon Mounts Legacy's own RegistrySetBuilder-
 * based dynamic-registry bootstrapping, which nothing else in this
 * project uses. Functionally equivalent for what this mod needs -
 * data-driven, datapack-editable, addon-mod-friendly - just built with
 * the same machinery as every other data-driven system already in this
 * codebase.
 *
 * habitats now actually parses (see com.dragonspeech.dragon.egg.Habitat
 * and its 7 concrete types) - defaults to an empty list, meaning "no
 * habitat requirement, always progresses" for any breed JSON that
 * doesn't specify one, rather than failing to parse.
 */
public record DragonBreed(
    int primaryColor,
    int secondaryColor,
    int growthTicks,
    float sizeModifier,
    List<String> immuneDamageTypes,
    Map<String, Double> attributeOverrides,
    Optional<String> deathLootTable,
    ModelVariation modelVariation,
    List<Habitat> habitats,
    float hatchChance
) {
    /** Matches Dragon Mounts Legacy's own default (10% per random-tick roll) - a starting point, not tuned specifically for this project's redesigned habitat-gating (see DragonEggHatchingBlockEntity's own doc for why habitat use here differs from theirs). */
    public static final float DEFAULT_HATCH_CHANCE = 0.1f;
    /** Matches Dragon Mounts Legacy's own BASE_GROWTH_TIME default (20 minutes, in ticks) - a breed JSON only needs to override this if it wants a different growth speed. */
    public static final int DEFAULT_GROWTH_TICKS = 24000;
    public static final float DEFAULT_SIZE_MODIFIER = 1.0f;

    /**
     * Small per-breed model shape toggles - what Dragon Mounts Legacy
     * calls "Properties", but relocated here (server-safe/shared code)
     * instead of living inside their DragonModel (client-only code in
     * this project's split source-set layout). DragonModel just reads
     * breed.modelVariation() rather than defining/owning this itself -
     * server-side code can never depend on client-only code in a
     * Fabric split-source project, which isn't a constraint Dragon
     * Mounts Legacy's own (non-split) project structure has to deal
     * with.
     */
    public record ModelVariation(boolean middleTailScales, boolean tailHorns, boolean thinLegs) {
        public static final ModelVariation STANDARD = new ModelVariation(true, false, false);

        public static final Codec<ModelVariation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("middle_tail_scales", true).forGetter(ModelVariation::middleTailScales),
            Codec.BOOL.optionalFieldOf("tail_horns", false).forGetter(ModelVariation::tailHorns),
            Codec.BOOL.optionalFieldOf("thin_legs", false).forGetter(ModelVariation::thinLegs)
        ).apply(instance, ModelVariation::new));
    }

    public static final Codec<DragonBreed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("primary_color").forGetter(DragonBreed::primaryColor),
        Codec.INT.fieldOf("secondary_color").forGetter(DragonBreed::secondaryColor),
        Codec.INT.optionalFieldOf("growth_ticks", DEFAULT_GROWTH_TICKS).forGetter(DragonBreed::growthTicks),
        Codec.FLOAT.optionalFieldOf("size_modifier", DEFAULT_SIZE_MODIFIER).forGetter(DragonBreed::sizeModifier),
        Codec.STRING.listOf().optionalFieldOf("immune_damage_types", List.of()).forGetter(DragonBreed::immuneDamageTypes),
        Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("attribute_overrides", Map.of()).forGetter(DragonBreed::attributeOverrides),
        Codec.STRING.optionalFieldOf("death_loot_table").forGetter(DragonBreed::deathLootTable),
        ModelVariation.CODEC.optionalFieldOf("model_variation", ModelVariation.STANDARD).forGetter(DragonBreed::modelVariation),
        Habitat.CODEC.listOf().optionalFieldOf("habitats", List.of()).forGetter(DragonBreed::habitats),
        Codec.FLOAT.optionalFieldOf("hatch_chance", DEFAULT_HATCH_CHANCE).forGetter(DragonBreed::hatchChance)
    ).apply(instance, DragonBreed::new));
}

