package com.dragonspeech.mind;

import com.dragonspeech.growth.AttunementAccess;
import com.dragonspeech.storage.SkillsAccess;
import com.dragonspeech.word.Domain;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Turns any LivingEntity into the numbers a duel actually runs on.
 * Players GROW their strength (MIND attunement, hugvarna, scars, mind
 * training); mobs are assigned a SentienceTier and read off it, WITH
 * random per-mob variance layered on top (see ROLL_VARIANCE) so two
 * mobs of the same tier - two Wardens, say - aren't perfectly identical
 * opponents. Neither path is hardcoded into the resolvers - they only
 * ever see the resulting MindCombatant, so a player and a mob are
 * interchangeable as far as the duel state machine is concerned.
 *
 * TIER_OVERRIDES is public and mutable on purpose: an addon mod (e.g. a
 * future dragon mod) can register its own EntityType -> SentienceTier
 * mapping at startup without touching this file - per the user's note,
 * dragons aren't added yet, but when they are, registering them at
 * SentienceTier.DRAGON here is the entire integration needed for them
 * to be, correctly, the hardest minds in the game to break.
 */
public final class MindFortitudeService {

    public static final Map<EntityType<?>, SentienceTier> TIER_OVERRIDES = new HashMap<>();

    /**
     * Added for the config GUI's Sentience Editor per explicit direction: "it should not be limited
     * to only the 3/4 tiers it has... The Higher the tier, the faster that mind reacts and defends/
     * attacks." SentienceTier itself stays as-is (it also drives fortitude/focus/stamina, not just
     * reaction speed, and replacing it outright would be a much bigger, riskier change than what was
     * actually asked for) - this is a SEPARATE, purely numeric override specifically for reaction
     * speed (MobMindCombatAI's clickAttemptsPerPulse), unbounded rather than locked to a handful of
     * enum-driven presets. An entity with no entry here just uses its SentienceTier's own built-in
     * default (see clickAttemptsPerPulse) - this map only ever holds entities someone has explicitly
     * fine-tuned beyond that.
     */
    public static final Map<EntityType<?>, Integer> REACTION_OVERRIDES = new HashMap<>();

    private static final Random RANDOM = new Random();
    /** How much a mob's stats can randomly vary from its tier's baseline, plus or minus. Keeps same-tier mobs from being perfectly interchangeable. */
    private static final float MOB_ROLL_VARIANCE = 0.15f;

    private static final float FORTITUDE_PER_ATTUNEMENT = 0.7f;
    private static final float FOCUS_PER_ATTUNEMENT = 1.2f;
    private static final float STAMINA_PER_ATTUNEMENT = 1.0f;

    private static final int HUGVARNA_FORTITUDE_BONUS = 20;
    private static final float HUGVARNA_WILLPOWER_BONUS = 15f;

    private MindFortitudeService() {}

    /** The passive resistance score ContactResolver rolls an attacker's skill against. Higher = harder to affect. */
    public static int fortitude(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            float attunement = AttunementAccess.get(player).get(Domain.MIND);
            int base = SentienceTier.SIMPLE.baseFortitude();
            int trained = base + Math.round(attunement * FORTITUDE_PER_ATTUNEMENT);
            if (SkillsAccess.get(player).canWallMind() && !com.dragonspeech.enchant.EquippedEnchantments.has(player, "hugopna")) {
                trained += HUGVARNA_FORTITUDE_BONUS;
            }
            return trained;
        }
        return classifyTier(entity).baseFortitude();
    }

    /** Fresh per-duel resource pools for this entity - call once, at duel start, never mid-duel. */
    public static MindCombatant buildCombatant(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            float attunement = AttunementAccess.get(player).get(Domain.MIND);
            float maxFocus = SentienceTier.SIMPLE.baseFocus() + attunement * FOCUS_PER_ATTUNEMENT;
            float maxStamina = SentienceTier.SIMPLE.baseStamina() + attunement * STAMINA_PER_ATTUNEMENT;
            float maxWillpower = 60f + attunement * 0.8f;
            float maxPower = 60f + attunement * 0.6f;
            float maxSpeed = 60f + attunement * 0.5f;
            float discipline = 20f + attunement * 0.4f;
            if (SkillsAccess.get(player).canWallMind() && !com.dragonspeech.enchant.EquippedEnchantments.has(player, "hugopna")) {
                maxWillpower += HUGVARNA_WILLPOWER_BONUS;
            }
            // Regen scales with the same attunement that grows the pools - a more
            // practiced mind doesn't just have bigger reserves, it recovers faster too.
            float regenScale = 1f + attunement / 100f;
            return new MindCombatant(
                maxFocus, maxStamina, maxWillpower, maxPower, maxSpeed, discipline,
                2.0f * regenScale, 4.0f * regenScale, 2.5f * regenScale, 2.5f * regenScale, 3.0f * regenScale);
        }

        SentienceTier tier = classifyTier(entity);
        float variance = 1f + (RANDOM.nextFloat() * 2f - 1f) * MOB_ROLL_VARIANCE;
        // See MindScaling - lets an individual entity (e.g. a young vs.
        // ancient dragon, both DRAGON-tier) scale its own pools beyond
        // what the tier alone implies. No-op 1.0 for anything that
        // doesn't implement it.
        float scale = entity instanceof MindScaling scaling ? scaling.mindPowerMultiplier() : 1f;
        float maxFocus = tier.baseFocus() * variance * scale;
        float maxStamina = tier.baseStamina() * variance * scale;
        float maxWillpower = Math.max(10f, tier.baseFortitude() * 0.7f) * variance * scale;
        float maxPower = Math.max(10f, tier.baseFortitude() * 0.6f) * variance * scale;
        float maxSpeed = Math.max(10f, tier.baseFortitude() * 0.5f) * variance * scale;
        float discipline = 15f;
        // Higher tiers don't just have bigger pools, they recover faster too - a Dragon
        // shrugging off pressure that would permanently wear down a lesser mind.
        float regenScale = Math.max(0.5f, tier.baseFortitude() / 60f);
        return new MindCombatant(
            maxFocus, maxStamina, maxWillpower, maxPower, maxSpeed, discipline,
            1.5f * regenScale, 3.0f * regenScale, 2.0f * regenScale, 2.0f * regenScale, 2.5f * regenScale);
    }

    public static SentienceTier classifyTier(LivingEntity entity) {
        SentienceTier override = TIER_OVERRIDES.get(entity.getType());
        if (override != null) {
            return override;
        }
        if (entity instanceof Warden) {
            return SentienceTier.CHAOTIC;
        }
        if (entity instanceof EnderMan) {
            return SentienceTier.CHAOTIC;
        }
        if (entity instanceof Villager) {
            return SentienceTier.SIMPLE;
        }
        // FIXED: passive farm/wild animals (chicken, cow, pig, sheep,
        // etc) are Animal entities, not Monster subtypes - they were
        // failing the hostile-mob check below and falling straight
        // through to the SIMPLE default, landing on the SAME tier as
        // villagers. That's what made them feel just as hard to duel as
        // an actual person. Checked BEFORE the Monster check specifically
        // so a passive animal never has a chance to match it anyway.
        if (entity instanceof net.minecraft.world.entity.animal.Animal) {
            return SentienceTier.TRIVIAL;
        }
        if (entity instanceof net.minecraft.world.entity.Mob mob && !mob.getType().getCategory().isFriendly()
            && mob instanceof net.minecraft.world.entity.monster.Monster) {
            return SentienceTier.INSTINCTUAL;
        }
        return SentienceTier.SIMPLE;
    }
}
