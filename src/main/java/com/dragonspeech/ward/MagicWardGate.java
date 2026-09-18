package com.dragonspeech.ward;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "The ward to block magic doesn't seem to work... I tested it with
 * marka illr" per explicit direction - and the reason was structural,
 * not a small bug: LivingEntityDamageMixin (the ONLY place the magic
 * ward was ever checked) only ever fires from LivingEntity.hurt() - but
 * MarkEffectHandler (and several others: gravity_scale, lift, confuse,
 * teleport, drain_stamina, drain_life...) apply their effect DIRECTLY,
 * with no damage and no hurt() call at all. A ward that only watches
 * hurt() can never see those. This is the general-purpose gate for
 * EVERYTHING ELSE, wired into CastRequestHandler's main cast dispatch
 * right before targets get handed to their effect handler - "This ward
 * blocks magic that is affecting you directly" per direction, checked
 * once per (caster, target, effect) rather than duplicated into every
 * individual handler.
 *
 * Same bypass rules as the damage-side magic ward check in
 * LivingEntityDamageMixin (self-cast always works, a GOOD-marked caster
 * always bypasses) - kept here rather than shared code since the two
 * call sites take different parameter shapes (DamageSource vs. a raw
 * caster/target pair), but the RULE is identical.
 */
public final class MagicWardGate {

    private MagicWardGate() {}

    /**
     * Effect ids exempt from magic-ward blocking entirely - "will not
     * be able to block spells that could throw projectiles at you,
     * blocks, explosions (since they have an area of effect)... this
     * ward protects against things that will directly affect the
     * player" per direction. hurl_block is a thrown block (a
     * projectile); wall/shape_block/sunder/barrier are block/structure
     * effects that don't target a living entity directly in the first
     * place; summon creates a new entity rather than acting on the
     * target at all.
     */
    private static final Set<String> EXEMPT_EFFECTS = Set.of(
            "hurl_block", "wall", "shape_block", "sunder", "barrier", "summon"
    );

    /** How much ward durability a blocked non-damage effect costs - there's no "incoming damage" number to drain against for something like marka, so this is a flat, moderate cost (roughly a light hit's worth) rather than free-forever blocking. */
    private static final float NON_DAMAGE_BLOCK_COST = 10f;

    /**
     * True if this effect was blocked (the caller should drop `target`
     * from the invocation's target list entirely - see
     * CastRequestHandler). Also handles the ward's own durability cost
     * and block feedback (sound/particles/message) when it blocks,
     * exactly like a damage-based block does.
     */
    public static boolean isBlocked(LivingEntity caster, LivingEntity target, String effectId) {
        if (caster == null || caster == target || EXEMPT_EFFECTS.contains(effectId)) {
            return false;
        }
        if (com.dragonspeech.engine.MarkRegistry.isGood(caster)) {
            return false; // "THE ONE IMPORTANT Exception... if you have marked a player (with the good mark)"
        }

        List<ActiveWard> list = new ArrayList<>(WardAccess.get(target).wards());
        for (int i = 0; i < list.size(); i++) {
            ActiveWard ward = list.get(i);
            if (ward.type() != WardType.MAGIC || ward.isBroken()) {
                continue;
            }

            float energyCost = Math.min(ward.remainingEnergy(), NON_DAMAGE_BLOCK_COST);
            float shortfall = NON_DAMAGE_BLOCK_COST - energyCost;
            // COMPILE FIX: DrainResolver.applyLethalDrain is ServerPlayer-
            // only - see WardService.absorb's matching guard for the
            // full reasoning (mobs don't have the player stamina system
            // aflbinda's fallback draws from).
            boolean canDrainStamina = ward.staminaBound() && target instanceof ServerPlayer;
            if (shortfall > 0.0001f && !canDrainStamina) {
                continue; // can't fully cover the block cost - try the next stacked ward, same rule as WardService.absorb
            }

            ActiveWard updated = ward.afterAbsorbing(energyCost);
            list.set(i, updated);
            if (shortfall > 0.0001f) {
                com.dragonspeech.stamina.DrainResolver.applyLethalDrain((ServerPlayer) target, shortfall);
            }

            WardAccess.set(target, new PlayerWards(List.copyOf(list)).withBrokenRemoved());
            WardService.pushSync(target);
            WardService.playBlockFeedback(target, WardType.MAGIC);
            if (target instanceof ServerPlayer defender) {
                defender.sendSystemMessage(Component.literal("Your ward against magic turns the working aside."));
            }
            return true;
        }
        return false;
    }
}