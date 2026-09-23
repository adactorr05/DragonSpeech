package com.dragonspeech.loot;

import com.dragonspeech.item.DragonSpeechItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.Map;

/**
 * Injects tablets and Scholar's Fragments into vanilla structure loot.
 * Tablet TIER scales with the structure's danger: surface ruins hold
 * worn tablets, temples and libraries hold ancient ones, and only the
 * deep places hold primordial inscriptions. Fragments (the translation
 * study-aids) are common in "scholarly" locations - libraries above all.
 *
 * VERSION-RISK NOTE: written against fabric-api's loot.v3 (current for
 * 1.21.x) - see the earlier note in git history; the MODIFY event shape
 * has stayed stable across the loot API's package moves.
 */
public final class LootInjection {

    private record Entry(Item item, float chance) {}

    private static final Map<ResourceKey<LootTable>, Entry> TABLETS = Map.of(
        BuiltInLootTables.RUINED_PORTAL, new Entry(DragonSpeechItems.WORD_TABLET_WORN, 0.20f),
        BuiltInLootTables.ABANDONED_MINESHAFT, new Entry(DragonSpeechItems.WORD_TABLET_WORN, 0.25f),
        BuiltInLootTables.SIMPLE_DUNGEON, new Entry(DragonSpeechItems.WORD_TABLET_WORN, 0.35f),
        BuiltInLootTables.DESERT_PYRAMID, new Entry(DragonSpeechItems.WORD_TABLET_ANCIENT, 0.40f),
        BuiltInLootTables.JUNGLE_TEMPLE, new Entry(DragonSpeechItems.WORD_TABLET_ANCIENT, 0.40f),
        BuiltInLootTables.STRONGHOLD_LIBRARY, new Entry(DragonSpeechItems.WORD_TABLET_ANCIENT, 0.60f),
        // FIX: "Primordial tablets are way too popular... if they appear
        // in the other 2 [structures], low chances (10% or below)" per
        // explicit direction - Ancient City wasn't one of the 4 custom
        // structures the user was thinking about, but it's exactly the
        // kind of "other location" that instruction applies to, and at
        // 65% on a chest-rich, frequently-explored vanilla structure it
        // was very likely the single biggest contributor to primordial
        // tablets feeling overly common. Reduced to 10% flat, matching
        // the stated threshold - Ancient City keeps its other rewards
        // (vanilla loot, scholars_fragment at 50% below, the rare
        // dragon egg chance further down) completely unchanged.
        BuiltInLootTables.ANCIENT_CITY, new Entry(DragonSpeechItems.WORD_TABLET_PRIMORDIAL, 0.10f)
    );

    private static final Map<ResourceKey<LootTable>, Float> FRAGMENTS = Map.of(
        BuiltInLootTables.STRONGHOLD_LIBRARY, 0.80f,
        BuiltInLootTables.VILLAGE_TEMPLE, 0.35f,
        BuiltInLootTables.SIMPLE_DUNGEON, 0.30f,
        BuiltInLootTables.DESERT_PYRAMID, 0.35f,
        BuiltInLootTables.ANCIENT_CITY, 0.50f,
        BuiltInLootTables.SHIPWRECK_MAP, 0.30f
    );

    /**
     * "Let them only spawn in strongholds, and other chests (extremely
     * rare) whereas strongholds have a single egg" per explicit
     * direction. Confirmed there was previously NO survival path to a
     * dragon egg at all - not in any loot table, recipe, or anywhere
     * else in the project.
     *
     * STRONGHOLD_LIBRARY at 90%, count fixed at 1: a stronghold can
     * generate with zero, one, or (rarely) more than one library room,
     * so this can't be a mathematically perfect "always exactly one per
     * stronghold" without a custom structure-piece system - but at 90%
     * chance on the library specifically (the room already used for the
     * tablet injection above, and thematically the right one for
     * something this significant), the overwhelming majority of
     * strongholds end up with exactly one, which is the closest a loot
     * table alone can get.
     *
     * The "other chests, extremely rare" spread deliberately reuses
     * ONLY loot table keys already proven to exist in THIS file
     * (TABLETS/FRAGMENTS above) rather than guessing at new
     * BuiltInLootTables constant names blind - lower risk than reaching
     * for ones this codebase has never referenced before.
     */
    private static final Map<ResourceKey<LootTable>, Float> DRAGON_EGG_RARE_CHESTS = Map.of(
        BuiltInLootTables.RUINED_PORTAL, 0.015f,
        BuiltInLootTables.ABANDONED_MINESHAFT, 0.015f,
        BuiltInLootTables.SIMPLE_DUNGEON, 0.02f,
        BuiltInLootTables.DESERT_PYRAMID, 0.025f,
        BuiltInLootTables.JUNGLE_TEMPLE, 0.025f,
        BuiltInLootTables.ANCIENT_CITY, 0.03f
    );

    private LootInjection() {}

    /** LootItemRandomChanceCondition.randomChance() expects (0, 1] - a multiplier could otherwise push it out of range. */
    private static float clampChance(float chance) {
        return Math.max(0.0f, Math.min(1.0f, chance));
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }

            // Config GUI (Server tab) "Word Loot Chance Multiplier" - scales both word-teaching loot
            // sources (tablets + scholar's fragments) below. Read once per loot-table (re)build, so a
            // change here takes effect on the next world load / datapack reload, not mid-session.
            float lootMultiplier = com.dragonspeech.config.DragonSpeechConfig.wordLootChanceMultiplier();

            Entry tablet = TABLETS.get(key);
            if (tablet != null) {
                tableBuilder.withPool(LootPool.lootPool()
                    .setRolls(UniformGenerator.between(1, 1))
                    .when(LootItemRandomChanceCondition.randomChance(clampChance(tablet.chance() * lootMultiplier)))
                    .add(LootItem.lootTableItem(tablet.item())));
            }

            Float fragmentChance = FRAGMENTS.get(key);
            if (fragmentChance != null) {
                tableBuilder.withPool(LootPool.lootPool()
                    .setRolls(UniformGenerator.between(1, 2))
                    .when(LootItemRandomChanceCondition.randomChance(clampChance(fragmentChance * lootMultiplier)))
                    .add(LootItem.lootTableItem(DragonSpeechItems.SCHOLARS_FRAGMENT)));
            }

            // The vanilla bonus chest loot table has a stable namespaced path even where the
            // mappings do not expose a convenient BuiltInLootTables constant. When enabled, add
            // exactly one random mod egg to every generated bonus chest.
            if (key.location().toString().equals("minecraft:chests/spawn_bonus_chest")
                    && com.dragonspeech.config.DragonSpeechConfig.bonusChestDragonEggEnabled()) {
                LootPool.Builder pool = LootPool.lootPool().setRolls(UniformGenerator.between(1, 1));
                addRandomEggEntries(pool);
                tableBuilder.withPool(pool);
            }

            if (key.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)) {
                LootPool.Builder pool = LootPool.lootPool()
                    .setRolls(UniformGenerator.between(1, 1))
                    .when(LootItemRandomChanceCondition.randomChance(0.90f));
                addRandomEggEntries(pool);
                tableBuilder.withPool(pool);
            }

            Float rareEggChance = DRAGON_EGG_RARE_CHESTS.get(key);
            if (rareEggChance != null) {
                LootPool.Builder pool = LootPool.lootPool()
                    .setRolls(UniformGenerator.between(1, 1))
                    .when(LootItemRandomChanceCondition.randomChance(rareEggChance));
                addRandomEggEntries(pool);
                tableBuilder.withPool(pool);
            }
        });
    }

    /**
     * "I want it to be 7 eggs... randomized from the 7 egg blocks" per
     * explicit direction - replaces the old single DragonSpeechItems.
     * DRAGON_EGG reference (removed - see DragonSpeechBlocks). A loot
     * pool rolling once with MULTIPLE entries added to it picks ONE at
     * random per roll, weighted equally here since every entry below
     * has the same (default) weight - the standard, correct vanilla way
     * to express "one random item from this list," not anything custom.
     */
    private static void addRandomEggEntries(LootPool.Builder pool) {
        for (net.minecraft.world.level.block.Block egg : com.dragonspeech.dragon.DragonSpeechBlocks.all()) {
            pool.add(LootItem.lootTableItem(egg.asItem()));
        }
    }
}
