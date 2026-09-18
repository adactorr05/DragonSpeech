package com.dragonspeech.stamina;

import com.dragonspeech.mob.casting.SpellcastingMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;

import java.util.HashMap;
import java.util.Map;

/**
 * How big an entity's notional stamina reserve is, and how fast it
 * regenerates - "there should be differences in their stamina" per
 * direction: a chicken shouldn't have the same pool as a villager,
 * which shouldn't have the same pool as a Shade.
 *
 * Two-tier lookup:
 *
 * 1. Anything implementing SpellcastingMob (Elf/Elder Elf/Human Mage/
 *    Shade, and any future magic-capable entity - Dragons included, once
 *    DragonEntity implements the interface) uses its own MobPowerTier's
 *    maxEnergy()/energyRegenPerSecond() DIRECTLY - the exact same numbers
 *    MobSpellComposer's cost-affordability math is already tuned
 *    against. This is the fix for "their stamina didn't go down at all
 *    when they were spamming spells": before this, SpellcastingMob had
 *    its OWN separate energy field entirely disconnected from this
 *    class (what /dragonspeech debug staminaview actually displays) -
 *    two parallel pools, only one of which ever moved. See
 *    SpellcastingMob's own doc for the fuller reasoning on why casting
 *    now spends from THIS pool instead.
 * 2. Everything else falls back to a small per-species table, checked
 *    by class (covers subspecies/variants for free - a mooshroom is
 *    still a Cow, a snow golem isn't listed so it falls through to
 *    DEFAULT) with a flat default for anything not explicitly listed.
 */
public final class MobStaminaScaling {

    private MobStaminaScaling() {}

    public static final float DEFAULT_MAX = 20f;
    private static final float DEFAULT_REGEN_PER_SECOND = DEFAULT_MAX / 33f; // full in ~33s - the original flat design's own pacing, kept as the fallback rate

    /**
     * Checked by CLASS (instanceof), not exact EntityType, top to bottom -
     * first match wins. A short, illustrative table, not an attempt to
     * cover every vanilla mob; add more entries here as needed.
     */
    private static final Map<Class<? extends LivingEntity>, Float> SPECIES_MAX = new HashMap<>();
    static {
        SPECIES_MAX.put(Chicken.class, 5f);
        SPECIES_MAX.put(Rabbit.class, 5f);
        SPECIES_MAX.put(Sheep.class, 8f);
        SPECIES_MAX.put(Pig.class, 8f);
        SPECIES_MAX.put(Cow.class, 10f);
        SPECIES_MAX.put(Wolf.class, 12f);
        SPECIES_MAX.put(Zombie.class, 15f);
        SPECIES_MAX.put(Skeleton.class, 15f);
        SPECIES_MAX.put(Spider.class, 15f);
        SPECIES_MAX.put(Villager.class, 20f);
        SPECIES_MAX.put(IronGolem.class, 40f);
        SPECIES_MAX.put(Witch.class, 60f);
        SPECIES_MAX.put(Warden.class, 300f);
        SPECIES_MAX.put(WitherBoss.class, 400f);
        SPECIES_MAX.put(EnderDragon.class, 500f);
    }

    public static float maxFor(LivingEntity entity) {
        if (entity instanceof SpellcastingMob caster) {
            return caster.powerTier().maxEnergy();
        }
        for (Map.Entry<Class<? extends LivingEntity>, Float> entry : SPECIES_MAX.entrySet()) {
            if (entry.getKey().isInstance(entity)) {
                return entry.getValue();
            }
        }
        return DEFAULT_MAX;
    }

    public static float regenPerSecond(LivingEntity entity) {
        if (entity instanceof SpellcastingMob caster) {
            return caster.powerTier().energyRegenPerSecond();
        }
        float max = maxFor(entity);
        if (max == DEFAULT_MAX) {
            return DEFAULT_REGEN_PER_SECOND;
        }
        return max / 33f; // same ~33s-to-full pacing as the default, scaled to this species' own pool size
    }
}
