package com.dragonspeech.ward;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Wards sit inert until triggered - placing one costs stamina once
 * (through the normal DrainResolver, at the calling site in
 * CastRequestHandler, not here), and this class only manages what
 * happens to the ward's stored energy once real damage tries to land.
 *
 * WIDENED from ServerPlayer to LivingEntity per explicit direction ("I
 * should be able to ward any entity") - see WardAccess's own doc.
 * Messaging and the client ring-sync payload only make sense for an
 * actual player, so those stay guarded behind `instanceof ServerPlayer`
 * rather than widening pointlessly - a warded mob still gets the same
 * block sound/particles everyone else sees, it just doesn't get a
 * system message or a ring overlay of its own.
 */
public final class WardService {

    private WardService() {}

    public static ActiveWard place(LivingEntity entity, WardType type, float energy, int charges, boolean visible, boolean staminaBound) {
        ActiveWard ward = new ActiveWard(UUID.randomUUID(), entity.getUUID(), type, energy, energy, charges, 0, visible, staminaBound);
        WardAccess.set(entity, WardAccess.get(entity).withAdded(ward));
        pushSync(entity);
        return ward;
    }

    /**
     * Called from LivingEntityDamageMixin for the matching WardType.
     * Returns EITHER the full incomingDamage (this hit was completely
     * blocked - the caller cancels the whole hurt() call, no damage, no
     * hurt sound, no red flash) OR 0 (nothing could fully cover it -
     * the hit goes through entirely normally, as if there were no ward
     * at all).
     *
     * REDESIGNED per explicit direction ("I want my wards to work
     * similar to how the [mob] entities work... stops the damage from
     * hitting you vs how it hits but nullifies"). The old version
     * partially absorbed - a ward drained whatever energy it had toward
     * the hit and let the rest through with a normal hurt animation,
     * which is exactly "hits but nullified" rather than "never hit at
     * all." It also explains the fall-ward report ("it says it's still
     * there but drinks from my own stamina instead of its durability
     * bar"): a staminaBound ward that ran out of its OWN energy mid-hit
     * used to keep going by draining stamina for the remainder rather
     * than declining to cover a hit it couldn't fully afford - that part
     * of the old design is unchanged (aflbinda's whole point is "never
     * truly breaks, drains you directly instead" - see its own
     * dictionary entry), but it should only ever trigger when the ward
     * doesn't have enough to fully cover on its own AND choosing to keep
     * covering is what aflbinda means. A ward WITHOUT aflbinda now
     * simply doesn't cover a hit bigger than what it has left at all
     * (falls through to the next stacked ward of that type, if any, or
     * to a normal unwarded hit if not) rather than spending itself down
     * to nothing for a partial block that still hurt you anyway.
     *
     * Multiple stacked wards of the same type (margfalt) still work:
     * this tries each unbroken ward of the matching type in turn until
     * one can fully cover the hit (or drains stamina for the shortfall,
     * if staminaBound), stopping at the first success.
     *
     * Now also plays the SAME block sound/colored particles the mob
     * side already had ("I want my wards to use the [same] noise when
     * they block an attack... this block noise on both players and
     * entities, should also sound when a magic attack hits the magic
     * ward" per direction) - see playBlockFeedback, also reused directly
     * by MagicWardGate for non-damage effects (mark, gravity, etc.) that
     * never reach this method at all since they don't deal damage.
     */
    public static float absorb(LivingEntity defender, WardType type, float incomingDamage) {
        PlayerWards wards = WardAccess.get(defender);
        List<ActiveWard> list = new ArrayList<>(wards.wards());
        boolean changed = false;
        Integer winningIndex = null;
        boolean winningWasStaminaCovered = false;

        // Pass 1: ordinary (non-staminaBound) wards, in stored order.
        // FIX: "if a ward cannot handle the damage input... it gets
        // stuck on 1 [durability]... this shouldn't happen, it should
        // collapse" per explicit direction - a ward that can't fully
        // cover a hit no longer just sits there untouched forever (the
        // old bug: it never got a chance to mutate at all, so a sliver
        // of leftover energy lingered permanently). It now drains
        // completely trying, then gets cleared out below - same idea as
        // a shield that shatters on a blow too strong for it, rather
        // than one that mysteriously stops working but never breaks.
        // The first one that CAN fully cover the hit wins outright and
        // stops the search (no further wards need to try or collapse).
        for (int i = 0; i < list.size() && winningIndex == null; i++) {
            ActiveWard ward = list.get(i);
            if (ward.type() != type || ward.isBroken() || ward.staminaBound()) {
                continue;
            }
            if (ward.remainingEnergy() >= incomingDamage) {
                winningIndex = i;
            } else {
                list.set(i, ward.afterAbsorbing(ward.remainingEnergy())); // drains to 0 - collapses attempting to cover it
                changed = true;
            }
        }

        // Pass 2: staminaBound wards, only if nothing else covered it.
        if (winningIndex == null) {
            for (int i = 0; i < list.size(); i++) {
                ActiveWard ward = list.get(i);
                if (ward.type() != type || ward.isBroken() || !ward.staminaBound()) {
                    continue;
                }
                if (ward.remainingEnergy() >= incomingDamage) {
                    winningIndex = i;
                    break;
                }
                if (!(defender instanceof ServerPlayer player)) {
                    continue; // can't drain stamina from a non-player wearer - this ward simply can't cover the shortfall
                }
                float shortfall = incomingDamage - ward.remainingEnergy();
                var result = com.dragonspeech.stamina.DrainResolver.applyLethalDrain(player, shortfall);
                changed = true;
                if (result.overdrafted()) {
                    // FIX: "if you run out of stamina, hunger and
                    // health... this should collapse the connected ward.
                    // Right now the ward will still protect you even if
                    // you don't have enough stamina to continue" per
                    // explicit direction - overdrafted() means item
                    // reserves + stamina + hunger + survivable health
                    // TOGETHER still couldn't cover the cost. The caster
                    // has nothing left to draw on, so this binding can't
                    // actually sustain itself anymore and collapses,
                    // same as running dry does for an ordinary ward -
                    // it does NOT keep "succeeding" forever regardless
                    // of the caster's state, which is what it did before.
                    //
                    // Removed directly rather than drained to 0 energy:
                    // isBroken() deliberately never returns true for a
                    // staminaBound ward from energy alone (see that
                    // method's own doc), so setting it to 0 here would
                    // leave it stuck in storage forever, unremovable -
                    // the exact bug this whole fix is for, just
                    // relocated to the staminaBound branch.
                    list.remove(i);
                    i--;
                    player.sendSystemMessage(Component.literal("Your bound ward finally collapses - you have nothing left to feed it."));
                    continue; // try the next staminaBound ward, if any
                }
                winningIndex = i;
                winningWasStaminaCovered = true;
                break;
            }
        }

        boolean shattered = false;
        if (winningIndex != null) {
            ActiveWard ward = list.get(winningIndex);
            if (!winningWasStaminaCovered) {
                ActiveWard updated = ward.afterAbsorbing(incomingDamage);
                list.set(winningIndex, updated);
                shattered = updated.isBroken();
            }
            // (the staminaCovered case already drained energy+stamina above, in the search itself)
            changed = true;
        }

        if (changed) {
            PlayerWards newWards = new PlayerWards(List.copyOf(list)).withBrokenRemoved();
            WardAccess.set(defender, newWards);
            pushSync(defender);
        }

        if (winningIndex == null) {
            return 0f; // nothing could fully cover this hit - not blocked at all (any wards that collapsed trying are already cleared above)
        }

        playBlockFeedback(defender, type);
        if (defender instanceof ServerPlayer player) {
            if (winningWasStaminaCovered) {
                player.sendSystemMessage(Component.literal("Your ward holds - but you feel it drink from you directly."));
            } else {
                player.sendSystemMessage(Component.literal(shattered
                    ? "A ward shatters, its purpose spent."
                    : "Your ward holds, drinking the blow."));
            }
        }

        return incomingDamage; // fully blocked
    }

    /**
     * The block sound + colored particle burst every ward absorption
     * now plays, lightweight player wards included - previously only
     * the mob-side MobWards had this at all. Colors copied verbatim
     * from WardRingRenderer's own per-type colors (same source the mob
     * side already matches), so this reads as the same visual language
     * everywhere a ward blocks something.
     */
    public static void playBlockFeedback(LivingEntity defender, WardType type) {
        if (!(defender.level() instanceof ServerLevel level)) {
            return;
        }
        Vector3f color = switch (type) {
            case PROJECTILE -> new Vector3f(0.90f, 0.78f, 0.30f); // gold
            case FIRE -> new Vector3f(0.95f, 0.35f, 0.15f);       // ember red
            case FALL -> new Vector3f(0.40f, 0.85f, 0.40f);       // green
            case EXPLOSION -> new Vector3f(0.95f, 0.55f, 0.15f);  // orange
            case MELEE -> new Vector3f(0.80f, 0.85f, 0.90f);      // pale steel
            case MAGIC -> new Vector3f(0.65f, 0.35f, 0.90f);      // violet
            // COMPILE FIX: WardType actually has a 7th value this switch
            // didn't account for - REVIVAL, the "aftrlifga sjalfan"
            // resurrection-binding, not a damage-blocking ward at all
            // (its own doc: "never returned by WardDamageMapper"). This
            // method should never actually be called with it in
            // practice, but the switch still has to be exhaustive to
            // compile - pale gold as an inert fallback color.
            case REVIVAL -> new Vector3f(0.85f, 0.80f, 0.55f);
        };
        DustParticleOptions options = new DustParticleOptions(color, 1.2f);
        level.sendParticles(options, defender.getX(), defender.getEyeY(), defender.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, defender.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.6f, 1.4f);
    }

    public static void disable(LivingEntity caster, UUID wardId) {
        WardAccess.set(caster, WardAccess.get(caster).withRemoved(wardId));
        pushSync(caster);
    }

    /** Syncs a player's own wards to their client for the ring visuals - type plus how full each ward still is. A no-op for anything that isn't a ServerPlayer (a warded mob has no client to sync to). */
    /**
     * Syncs a player's own wards to their client for the grimoire text
     * ("A ward should say 36/36... show if it's connected to Stamina"
     * per explicit direction) and the ring overlay's brightness. Used
     * to send ONLY a guessed 0-1 fraction (the ring renderer's own
     * need) with no real numbers at all - that's why the grimoire could
     * show a bar but never an actual number, and why the fraction math
     * itself was a guess (ActiveWard had no stored max to compute it
     * from properly - see that class's own doc). Now sends the real
     * current/max/staminaBound alongside a fraction computed from the
     * TRUE max, so the ring renderer (which only ever reads .fraction())
     * needs no changes at all.
     */
    /**
     * Syncs a player's own wards to their client for the grimoire text
     * ("A ward should say 36/36... show if it's connected to Stamina"
     * per explicit direction) and the ring overlay's brightness.
     *
     * For a staminaBound ward, remaining/max/fraction now report the
     * caster's ACTUAL STAMINA, not the ward's own (now largely
     * irrelevant) energy pool - "when I connect a ward to my own
     * stamina, the durability bar... should be displaying my stamina
     * since it's connected to my stamina. Right now it acts as though
     * it is the durability bar, so it shows the ward is low when my
     * actual stamina is still half full" per explicit direction. The
     * ward's own energy still gets used up first in combat (see
     * absorb()) - this is purely about what's actually meaningful to
     * show a player once a ward is stamina-bound, which is "how much
     * stamina do I have to keep feeding this," not a number that stops
     * mattering the moment the ward's own pool runs dry.
     */
    public static void pushSync(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        var staminaData = com.dragonspeech.stamina.StaminaAccess.get(player);
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (ActiveWard ward : WardAccess.get(player).wards()) {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("type", ward.type().getSerializedName());
            obj.addProperty("stamina_bound", ward.staminaBound());

            float remaining;
            float max;
            if (ward.staminaBound()) {
                remaining = staminaData.stamina();
                max = staminaData.maxStamina();
            } else {
                remaining = ward.remainingEnergy();
                max = ward.maxEnergy();
            }
            obj.addProperty("remaining", remaining);
            obj.addProperty("max", max);
            obj.addProperty("fraction", Math.max(0f, Math.min(1f, remaining / Math.max(max, 1f))));
            array.add(obj);
        }
        com.dragonspeech.network.DragonSpeechNetworking.sendWardSync(player, array.toString());
    }

    /** How many (unbroken) wards an entity currently has - this is what the E-menu presence indicator should show, without revealing what they do. */
    public static int wardCount(LivingEntity entity) {
        return WardAccess.get(entity).wards().size();
    }
}
