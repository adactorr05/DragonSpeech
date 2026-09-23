package com.dragonspeech.ward;

import com.dragonspeech.spell.SustainMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

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

    /** Keeps timed/caster-bound wards honest even when no damage event happens. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            for (ServerLevel level : server.getAllLevels()) {
                long now = level.getGameTime();
                for (var entity : level.getAllEntities()) {
                    if (!(entity instanceof LivingEntity living)) continue;
                    List<ActiveWard> current = WardAccess.get(living).wards();
                    if (current.isEmpty()) continue;
                    List<ActiveWard> live = new ArrayList<>();
                    boolean changed = false;
                    for (ActiveWard ward : current) {
                        boolean remove = ward.isExpired(now) || ward.isBroken();
                        if (!remove && ward.sustainMode() == SustainMode.CASTER) {
                            ServerPlayer caster = server.getPlayerList().getPlayer(ward.casterId());
                            remove = caster != null && com.dragonspeech.stamina.StaminaAccess.get(caster).stamina() <= 0f;
                        }
                        if (remove) changed = true; else live.add(ward);
                    }
                    if (changed) {
                        WardAccess.set(living, new PlayerWards(List.copyOf(live)));
                        pushSync(living);
                    }
                }
            }
        });
    }

    public static ActiveWard place(LivingEntity entity, UUID casterId, WardType type, float reserve, int charges,
                                   boolean visible, SustainMode mode, long expiresAt) {
        SustainMode actual = mode == null ? SustainMode.DURATION : mode;
        float stored = actual == SustainMode.RESERVE ? Math.max(1f, reserve) : 0f;
        ActiveWard ward = new ActiveWard(UUID.randomUUID(), casterId, type, stored, Math.max(1f, reserve),
            charges, 0, visible, actual == SustainMode.CASTER, actual, expiresAt);
        WardAccess.set(entity, WardAccess.get(entity).withAdded(ward));
        pushSync(entity);
        return ward;
    }

    /** Backward-compatible placement path for older callers. */
    public static ActiveWard place(LivingEntity entity, WardType type, float energy, int charges, boolean visible, boolean staminaBound) {
        return place(entity, entity.getUUID(), type, energy, charges, visible,
            staminaBound ? SustainMode.CASTER : SustainMode.RESERVE, -1L);
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
        if (!(defender.level() instanceof ServerLevel level)) return 0f;
        long now = level.getGameTime();
        List<ActiveWard> list = new ArrayList<>(WardAccess.get(defender).wards());
        boolean changed = false;

        for (int i = 0; i < list.size(); i++) {
            ActiveWard ward = list.get(i);
            if (ward.type() != type) continue;

            if (ward.isExpired(now) || ward.isBroken()) {
                list.remove(i--);
                changed = true;
                continue;
            }

            boolean covered = false;
            boolean collapsed = false;
            switch (ward.sustainMode()) {
                case DURATION -> {
                    covered = true;
                    list.set(i, ward.afterAbsorbing(0f));
                }
                case RESERVE -> {
                    if (ward.remainingEnergy() >= incomingDamage) {
                        ActiveWard updated = ward.afterAbsorbing(incomingDamage);
                        list.set(i, updated);
                        covered = true;
                        if (updated.isBroken()) collapsed = true;
                    } else {
                        // A reserve ward spends what remains trying to stop the blow, then collapses.
                        list.remove(i--);
                        changed = true;
                        continue;
                    }
                }
                case CASTER -> {
                    ServerPlayer caster = level.getServer().getPlayerList().getPlayer(ward.casterId());
                    if (caster == null) {
                        // Do not destroy a binding merely because its caster is temporarily offline.
                        continue;
                    }
                    var stamina = com.dragonspeech.stamina.StaminaAccess.get(caster);
                    if (stamina.stamina() + 0.0001f < incomingDamage) {
                        com.dragonspeech.stamina.StaminaAccess.set(caster, stamina.withStamina(0f));
                        list.remove(i--);
                        changed = true;
                        caster.sendSystemMessage(Component.literal("Your stamina-bound ward collapses as your stamina runs dry."));
                        continue;
                    }
                    com.dragonspeech.stamina.StaminaAccess.set(caster, stamina.withStamina(stamina.stamina() - incomingDamage));
                    list.set(i, ward.afterAbsorbing(0f));
                    covered = true;
                }
            }

            if (covered) {
                changed = true;
                if (collapsed) list.remove(i);
                WardAccess.set(defender, new PlayerWards(List.copyOf(list)).withBrokenRemoved());
                pushSync(defender);
                playBlockFeedback(defender, type);
                if (defender instanceof ServerPlayer player) {
                    String message = switch (ward.sustainMode()) {
                        case DURATION -> "Your timed ward turns the blow aside.";
                        case RESERVE -> collapsed ? "Your ward shatters as it blocks the blow." : "Your ward holds, spending its own reserve.";
                        case CASTER -> "Your bound ward holds, drawing directly from its caster's stamina.";
                    };
                    player.sendSystemMessage(Component.literal(message));
                }
                return incomingDamage;
            }
        }

        if (changed) {
            WardAccess.set(defender, new PlayerWards(List.copyOf(list)).withBrokenRemoved());
            pushSync(defender);
        }
        return 0f;
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
            case PROJECTILE -> new Vector3f(0.90f,0.78f,0.30f); case FIRE -> new Vector3f(0.95f,0.35f,0.15f);
            case LIGHTNING -> new Vector3f(0.30f,0.72f,1f); case WIND -> new Vector3f(0.74f,0.93f,1f);
            case ICE -> new Vector3f(0.64f,0.90f,1f); case WATER -> new Vector3f(0.18f,0.48f,0.84f);
            case POISON -> new Vector3f(0.33f,0.79f,0.21f); case FORCE -> new Vector3f(0.95f,0.89f,0.55f);
            case EARTH -> new Vector3f(0.54f,0.42f,0.23f); case LIGHT -> new Vector3f(1f,0.96f,0.72f);
            case SHADOW -> new Vector3f(0.21f,0.16f,0.30f); case DEATH -> new Vector3f(0.34f,0.14f,0.43f);
            case LIFE -> new Vector3f(0.55f,1f,0.55f); case VOID -> new Vector3f(0.20f,0.10f,0.30f);
            case FALL -> new Vector3f(0.40f,0.85f,0.40f); case EXPLOSION -> new Vector3f(0.95f,0.55f,0.15f);
            case MELEE -> new Vector3f(0.80f,0.85f,0.90f); case MAGIC -> new Vector3f(0.39f,0.85f,0.65f);
            case DANGER_LIFSKAD -> new Vector3f(0.78f,0.25f,0.32f); case DANGER_LIFROF -> new Vector3f(0.72f,0.18f,0.40f);
            case DANGER_LIFSLIT -> new Vector3f(0.62f,0.14f,0.50f); case DANGER_LIFSTILLA -> new Vector3f(0.49f,0.12f,0.58f);
            case DANGER_LIFTHAGN -> new Vector3f(0.33f,0.08f,0.48f); case REVIVAL -> new Vector3f(0.85f,0.80f,0.55f);
        };
        DustParticleOptions options = new DustParticleOptions(color, 1.2f);
        level.sendParticles(options, defender.getX(), defender.getEyeY(), defender.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, defender.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.6f, 1.4f);
    }

    public static void deflectProjectile(LivingEntity defender, net.minecraft.world.entity.Entity projectile) {
        if (projectile == null || projectile.isRemoved()) return;
        if (defender.level() instanceof ServerLevel level) level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, defender.getX(), defender.getEyeY(), defender.getZ(), 12, .35,.35,.35,.08);
        projectile.discard();
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
        if (!(entity instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        var ownStamina = com.dragonspeech.stamina.StaminaAccess.get(player);
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        List<ActiveWard> live = new ArrayList<>();

        for (ActiveWard ward : WardAccess.get(player).wards()) {
            if (ward.isExpired(now) || ward.isBroken()) continue;
            live.add(ward);
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("type", ward.type().getSerializedName());
            obj.addProperty("stamina_bound", ward.sustainMode() == SustainMode.CASTER);
            obj.addProperty("sustain_mode", ward.sustainMode().name().toLowerCase(java.util.Locale.ROOT));

            float remaining;
            float max;
            if (ward.sustainMode() == SustainMode.CASTER) {
                ServerPlayer caster = player.getServer().getPlayerList().getPlayer(ward.casterId());
                var data = caster == null ? ownStamina : com.dragonspeech.stamina.StaminaAccess.get(caster);
                remaining = data.stamina();
                max = data.maxStamina();
            } else if (ward.sustainMode() == SustainMode.DURATION) {
                remaining = ward.expiresAt() < 0L ? 0f : Math.max(0L, ward.expiresAt() - now);
                max = Math.max(1f, remaining);
            } else {
                remaining = ward.remainingEnergy();
                max = ward.maxEnergy();
            }
            obj.addProperty("remaining", remaining);
            obj.addProperty("max", max);
            obj.addProperty("fraction", ward.sustainMode() == SustainMode.DURATION
                ? (remaining > 0f ? 1f : 0f)
                : Math.max(0f, Math.min(1f, remaining / Math.max(max, 1f))));
            array.add(obj);
        }

        if (live.size() != WardAccess.get(player).wards().size()) {
            WardAccess.set(player, new PlayerWards(List.copyOf(live)));
        }
        com.dragonspeech.network.DragonSpeechNetworking.sendWardSync(player, array.toString());
    }

    /** How many (unbroken) wards an entity currently has - this is what the E-menu presence indicator should show, without revealing what they do. */
    public static int wardCount(LivingEntity entity) {
        return WardAccess.get(entity).wards().size();
    }
}
