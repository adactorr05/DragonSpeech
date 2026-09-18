package com.dragonspeech.stamina;

import com.dragonspeech.storage.ItemStaminaStorage;
import com.dragonspeech.storage.SkillsAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;

/**
 * The full drain cascade: charged storage items first (if the caster
 * knows how to draw on them), then stamina, then hunger, then health,
 * with a survivable floor on health by default. Every constant below is
 * a balance knob; the one genuinely load-bearing rule is the ORDER and
 * the fact that overdrafting still drains everything the caster had, as
 * a real consequence for overreaching.
 */
public final class DrainResolver {

    // --- Tunable balance constants --------------------------------------

    /** How much "energy" one point of hunger (out of 20) is worth once stamina runs out. */
    private static final float HUNGER_ENERGY_PER_POINT = 5.0f;

    /** How much "energy" one point of health (2 per heart) is worth once hunger runs out too. */
    private static final float HEALTH_ENERGY_PER_POINT = 2.0f;

    /**
     * Drain will not push health below this by default - a bad overdraft
     * knocks the caster into the collapse state below rather than killing
     * them. This should become a real per-world/server difficulty setting
     * (see the design notes on Easy/Normal/Hard/Hardcore magic stakes)
     * rather than a hardcoded constant - flagging that as near-term config
     * work, not a Phase 1 blocker.
     */
    private static final float MIN_SURVIVABLE_HEALTH = 1.0f;

    private static final int OVERDRAFT_WEAKNESS_TICKS = 200;
    private static final int OVERDRAFT_SLOWNESS_TICKS = 200;
    private static final int OVERDRAFT_BLINDNESS_TICKS = 100;
    private static final int EXHAUSTION_SLOWNESS_TICKS = 60;

    private DrainResolver() {}

    /** True only if the player has an eligible bonded dragon with the feature on AND set to draw before their own stamina. */
    private static boolean playerWantsDragonAssistBefore(ServerPlayer player) {
        return com.dragonspeech.dragon.DragonEntity.findNearestBonded(player, 32.0)
            .map(d -> d.useDragonStamina() && d.staminaBeforeOwn())
            .orElse(false);
    }

    /** True only if the player has an eligible bonded dragon with the feature on AND set to draw after their own stamina. */
    private static boolean playerWantsDragonAssistAfter(ServerPlayer player) {
        return com.dragonspeech.dragon.DragonEntity.findNearestBonded(player, 32.0)
            .map(d -> d.useDragonStamina() && !d.staminaBeforeOwn())
            .orElse(false);
    }

    /**
     * Convenience overload for every call site that isn't a normal
     * player-initiated spell cast (channel upkeep, backlash, ward
     * defense, the Word-of-Words easter egg, etc.) - none of those have
     * a spoken sentence to check for "telja", and none of them SHOULD
     * be reaching into the player's general inventory for a random
     * charged gem anyway. Equipped accessory slots still apply
     * regardless (see applyDrain's Tier 0 below) - only the "telja"-gated
     * general-inventory tier is skipped here.
     */
    public static DrainResult applyDrain(ServerPlayer player, float cost) {
        return applyDrain(player, cost, false);
    }

    public static DrainResult applyDrain(ServerPlayer player, float cost, boolean includeGeneralInventory) {
        PlayerMagicData magic = StaminaAccess.get(player);
        float remaining = cost;

        // --- Tier -0.5: Dragon Hearts, "before" setting ---
        // Checked before ordinary storage items - see DragonHeartAccess for
        // why. Deliberately NOT gated on gatherStamina: a heart answering
        // to you (PERMITTED or BROKEN) is a much stronger statement than
        // merely knowing the skynja+draga skills, so it works on its own.
        // Only fires for hearts with HEART_STAMINA_BEFORE_OWN set true
        // (the default) - see DragonHeartAccess.drainBefore.
        float eldunariEnergyUsed = com.dragonspeech.eldunari.DragonHeartAccess.drainBefore(player, remaining);
        remaining -= eldunariEnergyUsed;

        // --- Tier 0a: equipped accessory slots - ALWAYS active, no word
        // needed. This is the entire point of the 4 accessory slots: a
        // charged gem worn there just works, automatically, every cast.
        float itemEnergyUsed = 0f;
        if (SkillsAccess.get(player).gatherStamina()) {
            itemEnergyUsed += ItemStaminaStorage.drainAccessories(player, remaining);
            remaining = cost - eldunariEnergyUsed - itemEnergyUsed;

            // --- Tier 0b: ordinary inventory items - ONLY when "telja"
            // was actually spoken for this cast. A charged gem sitting
            // loose in your pack does nothing on its own anymore - see
            // ItemStaminaStorage.drainGeneralInventory's own doc for why
            // that changed.
            if (includeGeneralInventory && remaining > 0f) {
                float generalUsed = ItemStaminaStorage.drainGeneralInventory(player, remaining);
                itemEnergyUsed += generalUsed;
                remaining -= generalUsed;
            }
        }

        // --- Tier 0.5: bonded dragon auto-assist, "before" mode ---
        // See BondedDragonAssist and DragonEntity's Dragon Bond GUI
        // settings. Only one of the two bonded-dragon call sites in this
        // method actually fires for a given player - staminaBeforeOwn()
        // decides which - so the dragon is drained at most once per cast
        // regardless of setting.
        float dragonAssistBefore = 0f;
        if (playerWantsDragonAssistBefore(player)) {
            dragonAssistBefore = com.dragonspeech.dragon.BondedDragonAssist.drain(player, remaining);
            remaining -= dragonAssistBefore;
        }

        // --- Tier 1: stamina ---------------------------------------------
        float staminaUsed = Math.min(remaining, magic.stamina());
        remaining -= staminaUsed;

        // --- Tier 1.5: bonded dragon auto-assist, "after" mode ---
        float dragonAssistAfter = 0f;
        if (remaining > 0f && playerWantsDragonAssistAfter(player)) {
            dragonAssistAfter = com.dragonspeech.dragon.BondedDragonAssist.drain(player, remaining);
            remaining -= dragonAssistAfter;
        }

        // --- Tier 1.6: Dragon Hearts, "after" setting ---
        // Same hearts as Tier -0.5 above, but only the ones with
        // HEART_STAMINA_BEFORE_OWN set false - see DragonHeartAccess.
        // drainAfter. A given heart only ever fires from ONE of these
        // two tiers per cast, never both, since drainBefore/drainAfter
        // each only match hearts whose OWN setting agrees with which
        // tier is currently running.
        float eldunariEnergyUsedAfter = 0f;
        if (remaining > 0f) {
            eldunariEnergyUsedAfter = com.dragonspeech.eldunari.DragonHeartAccess.drainAfter(player, remaining);
            remaining -= eldunariEnergyUsedAfter;
        }

        // --- Tier 2: hunger (only reached once stamina is fully spent) ---
        // Blessing of the Warm Hearth ("arinheill") - +20% effective
        // energy per hunger point per level, worn only - hunger fades
        // slower because each point covers more of the remaining cost,
        // not because points are skipped outright.
        float warmHearthMultiplier = 1f + 0.2f * com.dragonspeech.enchant.EquippedEnchantments.levelOf(player, "arinheill");
        float effectiveHungerEnergyPerPoint = HUNGER_ENERGY_PER_POINT * warmHearthMultiplier;

        float hungerUsed = 0f;
        if (remaining > 0f) {
            float hungerAvailableEnergy = player.getFoodData().getFoodLevel() * effectiveHungerEnergyPerPoint;
            hungerUsed = Math.min(remaining, hungerAvailableEnergy);
            remaining -= hungerUsed;
        }

        // --- Tier 3: health, down to the survivable floor -----------------
        float healthUsed = 0f;
        if (remaining > 0f) {
            float survivableHealth = Math.max(0f, player.getHealth() - com.dragonspeech.config.DragonSpeechConfig.minSurvivableHealth());
            float healthAvailableEnergy = survivableHealth * HEALTH_ENERGY_PER_POINT;
            healthUsed = Math.min(remaining, healthAvailableEnergy);
            remaining -= healthUsed;
        }

        boolean overdrafted = remaining > 0f;

        // Everything up to this point was only arithmetic - now actually apply it.
        // (Item energy was already applied above, inside ItemStaminaStorage.drain.)
        StaminaAccess.set(player, magic.withStamina(magic.stamina() - staminaUsed));

        if (hungerUsed > 0f) {
            int hungerPointsToConsume = (int) Math.ceil(hungerUsed / effectiveHungerEnergyPerPoint);
            FoodData foodData = player.getFoodData();
            foodData.setFoodLevel(Math.max(0, foodData.getFoodLevel() - hungerPointsToConsume));
            applyExhaustionFeedback(player);
        }

        if (healthUsed > 0f) {
            float healthPointsToConsume = healthUsed / HEALTH_ENERGY_PER_POINT;
            float floor = com.dragonspeech.config.DragonSpeechConfig.minSurvivableHealth();
            if (floor <= 0f && player.getHealth() - healthPointsToConsume <= 0f) {
                // HARDCORE stakes: the working takes everything. hurt() through
                // the generic magic damage source so death messages/stats work,
                // rather than silently setting health to zero.
                player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
            } else {
                player.setHealth(Math.max(floor, player.getHealth() - healthPointsToConsume));
            }
        }

        if (overdrafted) {
            applyOverdraftCollapse(player);
        }

        return new DrainResult(itemEnergyUsed, staminaUsed, hungerUsed, healthUsed, overdrafted);
    }

    /**
     * The same drain cascade as applyDrain(), with one difference: if the
     * cost exceeds EVERYTHING the caster has - item reserves, stamina,
     * hunger, and health down to the survivable floor - the leftover
     * isn't absorbed into a harmless "overdraft collapse" debuff. It's
     * dealt as real damage, which can kill the player.
     *
     * This exists for exactly one thing right now: an aflbinda-bound
     * ward that has run out of stored energy. That binding's whole
     * premise is "protection funded by your own strength, not a
     * fixed reserve" - if it could never actually cost you your life,
     * it would just be a free, permanent immunity to whatever it wards
     * against (a player at half a heart would be unkillable by that
     * damage type forever, which defeats the point of it being a real
     * binding rather than a shield with unlimited charges). The
     * survivable floor still cushions the FIRST portion of a hit here,
     * same as any other drain - it's only the portion beyond what the
     * caster has to give at all that becomes lethal.
     *
     * The caster's actual way out is the same as for any binding:
     * speaking the control word against it ("letta <binding word>")
     * releases the ward outright, for free, before it ever reaches this
     * point - see CastRequestHandler.dispelOwnWards.
     *
     * The lethal remainder is applied on the NEXT server tick rather
     * than immediately, since this is called from inside
     * LivingEntityDamageMixin's hurt() interception - calling hurt()
     * again synchronously from there would re-enter the same damage
     * pipeline mid-flight. See LethalDrainQueue.
     */
    public static DrainResult applyLethalDrain(ServerPlayer player, float cost) {
        DrainResult result = applyDrain(player, cost);

        if (result.overdrafted()) {
            float covered = result.itemEnergySpent() + result.staminaSpent()
                + result.hungerEnergySpent() + result.healthEnergySpent();
            float leftoverEnergy = cost - covered;

            if (leftoverEnergy > 0f) {
                float lethalHealthDamage = leftoverEnergy / HEALTH_ENERGY_PER_POINT;
                LethalDrainQueue.queue(player, lethalHealthDamage);
            }
        }

        return result;
    }

    /** A light debuff any time hunger gets touched at all - draining hunger should always feel bad, not just at the extreme. */
    private static void applyExhaustionFeedback(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUSTION_SLOWNESS_TICKS, 0));
    }

    /**
     * The non-lethal "collapse" state for a bad overdraft - mirrors a
     * caster overexerting themselves in the source material. Placeholder
     * vanilla effects for now; a proper custom "Overdrawn" status effect
     * with its own name/icon is good Phase 8 polish, not required for
     * this to function correctly.
     */
    private static void applyOverdraftCollapse(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, OVERDRAFT_WEAKNESS_TICKS, 2));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, OVERDRAFT_SLOWNESS_TICKS, 3));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, OVERDRAFT_BLINDNESS_TICKS, 0));
    }
}
