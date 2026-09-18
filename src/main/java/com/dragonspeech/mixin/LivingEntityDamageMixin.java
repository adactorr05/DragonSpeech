package com.dragonspeech.mixin;

import com.dragonspeech.ward.WardDamageMapper;
import com.dragonspeech.ward.WardService;
import com.dragonspeech.ward.WardType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Shrinks incoming damage by whatever a matching ward absorbs, before
 * vanilla's own damage pipeline (armor, resistance, absorption hearts)
 * runs.
 *
 * REDESIGNED per explicit direction ("I want my wards to work similar
 * to how the [mob] entities work... stops the damage from hitting you
 * vs how it hits but nullifies"). Reducing `amount` via @ModifyVariable
 * (the ENTIRE old design) turned out NOT to suppress vanilla's hurt
 * sound/red-flash/knockback even when the reduced amount was exactly
 * 0 - those play regardless of how much damage actually lands, as long
 * as hurt() itself isn't cancelled outright. That's the confirmed cause
 * of every symptom reported ("protects me, but still does the hurt
 * animation" - true for fall/fire/melee/projectile alike, since ALL of
 * them only ever reduced the amount, never cancelled the call). Fully
 * blocking now genuinely cancels the whole hurt() call via a SEPARATE,
 * cancellable injector (dragonspeech$wardBlockFully below) - see
 * WardService.absorb's own doc for the matching full-block-or-nothing
 * redesign on the ward-state side.
 *
 * MAGIC ward scope, per explicit direction: "players can cast spells on
 * themselves just fine, but if another team player wants to give you a
 * boost... the magic ward will block it unless you disable the ward...
 * THE ONE IMPORTANT Exception... if you have marked a player (with the
 * good mark)." dragonspeech$magicWardBypassed below is exactly that
 * check - self-cast always bypasses, a GOOD-marked caster always
 * bypasses (reusing the SAME "GOOD passes through wards/cages" rule
 * MagicBarrierEntity already established for EntityMark - see that
 * enum's own doc), everyone else gets blocked same as any other magic
 * damage. This deliberately does NOT touch WardDamageMapper - "will not
 * be able to block spells that could throw projectiles at you, blocks,
 * explosions" already falls out of that mapper's existing priority
 * order (PROJECTILE/EXPLOSION/FIRE/FALL are all checked and claimed
 * BEFORE MAGIC gets a chance to), so nothing there needed to change.
 *
 * VERSION-RISK NOTE (real, not just boilerplate this time): this relies
 * on dragonspeech$wardBlockFully - an @Inject at @At("HEAD") - being
 * woven in and executing BEFORE dragonspeech$wardAbsorb - a
 * @ModifyVariable also at @At("HEAD") - so that cancelling the former
 * skips the latter entirely. Sponge Mixin applies same-priority
 * injectors in declaration order within a mixin class, which is why the
 * cancelling method is declared FIRST in this file - but if partial
 * ward absorption or double-processing ever reappears, this ordering
 * assumption is the first thing to check (try adding an explicit
 * `priority` to dragonspeech$wardBlockFully lower than the default 1000
 * to force it earlier).
 *
 * VERSION-RISK NOTE: this targets LivingEntity.hurt(DamageSource, float),
 * the long-standing damage entry point under Mojang mappings. If the
 * mixin fails to apply at runtime (crash on load mentioning this class,
 * or a "target method not found" error in the log), check LivingEntity
 * in your decompiled sources for the current name of the (DamageSource,
 * float)->boolean damage method and update the method target here.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    private static boolean dragonspeech$magicWardBypassed(LivingEntity defender, DamageSource source) {
        net.minecraft.world.entity.Entity caster = source.getEntity();
        if (caster == null) {
            return false;
        }
        if (caster == defender) {
            return true; // "players can cast spells on themselves just fine"
        }
        return com.dragonspeech.engine.MarkRegistry.isGood(caster); // the good-mark exception
    }

    /**
     * Cancels the ENTIRE hurt() call - no damage, no hurt sound, no red
     * flash, nothing - when a lightweight (spoken "verja") ward fully
     * covers the hit. Works for ANY LivingEntity now, not just
     * ServerPlayer - "I should be able to ward any entity" per explicit
     * direction, see WardAccess/WardService's own docs for the storage
     * widening this depends on. Declared FIRST in this file so it's
     * woven in before dragonspeech$wardAbsorb below - see the
     * class-level VERSION-RISK note.
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void dragonspeech$wardBlockFully(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (amount <= 0f || !((Object) this instanceof LivingEntity defender)) {
            return;
        }
        Optional<WardType> type = WardDamageMapper.fromDamageSource(source);
        if (type.isEmpty() || (type.get() == WardType.MAGIC && dragonspeech$magicWardBypassed(defender, source))) {
            return;
        }
        float absorbed = WardService.absorb(defender, type.get(), amount);
        if (absorbed >= amount - 0.0001f) {
            cir.setReturnValue(false);
        }
        // Not fully covered (absorbed == 0, per WardService.absorb's new
        // full-or-nothing contract - see its own doc): do nothing here,
        // let the hit proceed completely normally, including a normal
        // hurt animation - the ward genuinely didn't protect against
        // this one.
    }

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float dragonspeech$wardAbsorb(float amount, DamageSource source) {
        // Mind-duel interruption applies to EITHER combatant - attacker or
        // defender, player or mob - so this check runs before the
        // ServerPlayer-only ward/wound logic below, on whatever this
        // entity actually is.
        if (amount > 0f && (Object) this instanceof LivingEntity livingEntity
            && livingEntity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.dragonspeech.mind.MindDuelService.onPhysicalDamage(serverLevel.getServer(), livingEntity);
            com.dragonspeech.mind.MindDuelService.onPhysicalDamageTeam(serverLevel.getServer(), livingEntity);
            com.dragonspeech.mind.MindControlService.onPhysicalDamage(serverLevel.getServer(), livingEntity);
        }

        // (Object) cast is the standard mixin idiom for "is the entity
        // this mixin is attached to actually a X" - a plain instanceof
        // on 'this' won't compile since the mixin class itself isn't a
        // LivingEntity subtype at compile time.
        if (!((Object) this instanceof ServerPlayer player)) {
            return amount;
        }
        if (amount <= 0f) {
            return amount;
        }

        // Lightweight (spoken) ward absorption moved to
        // dragonspeech$wardBlockFully above, which cancels the whole
        // call when it fully covers a hit - if we're still running here,
        // that either didn't apply (wrong damage type/bypassed) or
        // didn't fully cover it, so `amount` is unchanged by it either
        // way. Only item-enchanted wards (galdrverja etc.) are handled
        // here now.
        Optional<WardType> type = WardDamageMapper.fromDamageSource(source);
        float through = amount;
        if (type.isPresent()) {
            float itemWardAbsorbed = com.dragonspeech.enchant.MagicWardCombat.absorb(player, type.get(), through);
            through = Math.max(0f, through - itemWardAbsorbed);
        }

        // What actually lands is remembered by its NATURE - burns are not
        // broken bones - so healing can (and must) name the wound it mends.
        // Unwardable damage (drowning, magic, starving) records as GENERIC.
        if (through > 0f) {
            com.dragonspeech.wound.WoundType woundType = com.dragonspeech.wound.WoundType.fromDamageSource(source);
            com.dragonspeech.wound.WoundAccess.set(player,
                com.dragonspeech.wound.WoundAccess.get(player).withAdded(woundType, through));
        }

        return through;
    }
}
