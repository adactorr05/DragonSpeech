package com.dragonspeech.worldgen;

import com.dragonspeech.entity.DragonSpeechEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Natural world spawning for all four races and wild Dragons - none of
 * this existed before at all (confirmed by searching the whole codebase
 * for SpawnPlacements.register - zero hits for any of them). Every one
 * of these could previously only ever be brought into a world via
 * /summon or /dragonspeech summon, both admin-only - "my 4 entities
 * spawn naturally in the world" was a flat no, and it cascaded into
 * "mentor npc"/"elven trial" word discovery being unreachable too, since
 * both require actually encountering these mobs.
 *
 * Two separate registrations per entity, same as any vanilla mob needs:
 * - SpawnPlacements.register: the PHYSICAL validity check (is this spot
 *   even a legal place for this kind of thing to appear) - reuses
 *   standard vanilla predicates (Mob::checkMobSpawnRules for the four
 *   that are plain Mob/PathfinderMob implementations, Monster::
 *   checkMonsterSpawnRules for Shade, which genuinely does extend
 *   Monster), not anything custom.
 * - BiomeModifications.addSpawn: WHICH biomes, and at what relative
 *   weight against everything else that can spawn there.
 *
 * RARITY (per explicit direction - "shades rare, elders rare, elves
 * less rare, human mages even less rare... wild dragons rare as
 * shades"): weights of 3/3/8/14/3 below are chosen relative to EACH
 * OTHER first (satisfying that exact ordering) and second against
 * typical vanilla spawn weights in the same category (a hostile weight
 * of 3 next to a zombie's ~95 or skeleton's ~100 reads as genuinely
 * rare, not just "rare among Dragon Speech mobs"). These are starting
 * points, not tuned by playtesting - easy to adjust in one place if
 * they feel off in practice.
 *
 * BIOME CHOICES (my own judgment call, not something you specified -
 * flagging clearly rather than assuming you'd agree): Elf/Elder Elf in
 * forests (thematic fit), Human Mage broadly across the whole overworld
 * (matching "even less rare" - the most commonly-encountered of the
 * four), Shade broadly across the whole overworld too (matching how
 * vanilla hostiles themselves aren't biome-restricted - kept rare by
 * weight alone, not narrow habitat), wild Dragon restricted to
 * mountain-family biomes specifically (both the weight AND the narrow
 * habitat make it feel special, matching "nests in high places").
 *
 * VERSION-RISK NOTE: SpawnPlacements.register() must be called exactly
 * once per entity type over the game's lifetime (calling it twice for
 * the same EntityType is a real vanilla footgun, not a Fabric-specific
 * one) - this file's register() is only ever invoked once from
 * DragonSpeech.java's own bootstrap, same as every other one-time
 * registration call there.
 *
 * CONFIRMED BREAKS (fixed, noted here for anyone hitting the same thing
 * again in a future version bump): Heightmap lives in
 * net.minecraft.world.level.levelgen, not net.minecraft.world.level -
 * moved packages at some point. SpawnPlacements.Type doesn't exist at
 * all in 1.21.1 - the placement-type constants (ON_GROUND, etc.) moved
 * out to their own standalone SpawnPlacementTypes class instead of
 * being a nested enum on SpawnPlacements itself. A third, different
 * kind of mistake (not an API-location guess, an actual class-hierarchy
 * one): Animal::checkAnimalSpawnRules requires T to genuinely extend
 * Animal - MobCategory.CREATURE (used for these registrations) is just
 * a spawn-cap category, unrelated to Java class hierarchy, and none of
 * Elf/Elder Elf/Human Mage/Dragon actually extend Animal. Mob::
 * checkMobSpawnRules is the correct generic predicate for a plain Mob
 * that isn't specifically an Animal or Monster subclass. All three
 * compiled clean by my own reasoning but were wrong in practice - worth
 * remembering that "this API shape is extremely standard and I've seen
 * it a hundred times" doesn't mean the exact package/class boundaries
 * survived a given version.
 *
 * Dragon's mountain habitat deliberately uses explicit Biomes.* keys
 * (BiomeSelectors.includeByKey) rather than a mountain-family TAG - tag
 * names in that particular family have moved around across versions
 * more than most, so naming the actual biomes directly is the
 * lower-risk choice here; BiomeTags.IS_FOREST (used for Elf/Elder Elf)
 * is much more stable and long-standing. If any single
 * line in this file doesn't compile, the explicit Biomes.* constant
 * names are the first thing worth checking against your decompiled
 * sources - some biome names have shifted between versions.
 */
public final class DragonSpeechSpawns {

    private DragonSpeechSpawns() {}

    public static void register() {
        // FIX: Animal::checkAnimalSpawnRules requires T to actually
        // extend Animal in the Java class hierarchy - MobCategory.
        // CREATURE (used when registering these 4) is just a spawn-cap
        // category, completely unrelated to Java class hierarchy. None
        // of Elf/Elder Elf/Human Mage/Dragon extend Animal (they're
        // plain Mob/PathfinderMob implementations), so the compiler
        // correctly rejected it. Mob::checkMobSpawnRules is the generic
        // equivalent that works for any Mob subtype, not specifically
        // Animal - Shade below is unaffected by this since it genuinely
        // does extend Monster, which is why only these 4 errored and
        // Shade's registration compiled clean the first time.
        SpawnPlacements.register(DragonSpeechEntities.ELF, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
        SpawnPlacements.register(DragonSpeechEntities.ELDER_ELF, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
        SpawnPlacements.register(DragonSpeechEntities.HUMAN_MAGE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
        SpawnPlacements.register(DragonSpeechEntities.SHADE, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
        SpawnPlacements.register(DragonSpeechEntities.DRAGON, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);

        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_FOREST),
                MobCategory.CREATURE, DragonSpeechEntities.ELF, 8, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_FOREST),
                MobCategory.CREATURE, DragonSpeechEntities.ELDER_ELF, 3, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(),
                MobCategory.CREATURE, DragonSpeechEntities.HUMAN_MAGE, 14, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(),
                MobCategory.MONSTER, DragonSpeechEntities.SHADE, 3, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(
                        Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST,
                        Biomes.STONY_PEAKS, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.SNOWY_SLOPES),
                MobCategory.CREATURE, DragonSpeechEntities.DRAGON, 3, 1, 1);
    }
}