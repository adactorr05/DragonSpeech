package com.dragonspeech.storage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Deliberately uses existing vanilla items as storage media rather than
 * adding new custom items - no new textures/models needed, and it fits
 * the source material well (gems and metals as the physical vessel for
 * stored strength, not a bespoke "mana crystal" item).
 */
public final class StorageMediumRegistry {

    public record MediumProperties(float maxCapacity, float decayPerMinute) {}

    private static final Map<Item, MediumProperties> MEDIUMS = new HashMap<>();

    static {
        // Capacities raised substantially from their original values -
        // those were tuned early, before real spell costs existed, and
        // left almost no headroom against an actual cast (a single
        // mid-power spell could cost more than a fully-charged gold
        // ingot held outright). Decay-per-minute unchanged; only ceiling
        // moved.
        MEDIUMS.put(Items.GOLD_INGOT, new MediumProperties(120f, 1.0f));      // common, decays - "weaker" medium
        MEDIUMS.put(Items.EMERALD, new MediumProperties(300f, 0f));          // no decay
        MEDIUMS.put(Items.DIAMOND, new MediumProperties(500f, 0f));          // no decay, higher capacity
        MEDIUMS.put(Items.NETHERITE_INGOT, new MediumProperties(900f, 0f));  // rare, best medium
    }

    private StorageMediumRegistry() {}

    /** Lets other classes (accessory jewelry, most likely) register themselves as valid storage media without editing this file's own hardcoded block. */
    public static void register(Item item, MediumProperties properties) {
        MEDIUMS.put(item, properties);
    }

    public static Optional<MediumProperties> propertiesOf(Item item) {
        return Optional.ofNullable(MEDIUMS.get(item));
    }

    public static boolean isValidMedium(Item item) {
        return MEDIUMS.containsKey(item);
    }
}
