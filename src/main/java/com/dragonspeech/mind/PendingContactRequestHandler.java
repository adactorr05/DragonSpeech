package com.dragonspeech.mind;

import com.dragonspeech.stamina.StaminaAccess;
import net.minecraft.server.level.ServerPlayer;

/** Handles HastenContactPayload - spends Stamina to shorten the player's own in-flight Contact attempt. */
public final class PendingContactRequestHandler {

    /** How much remaining travel time one "hasten tick" removes, and what it costs. Called at a throttled rate by the client while the reach key is held (see DragonSpeechClient). */
    private static final long TICKS_REMOVED_PER_HASTEN = 2L;
    private static final float STAMINA_COST_PER_HASTEN = 4f;

    private PendingContactRequestHandler() {}

    public static void handleHasten(ServerPlayer player) {
        var pending = PendingContactManager.get(player.getUUID()).orElse(null);
        if (pending == null) {
            return; // no active reach to hasten - a stale client message, not an error
        }
        var magic = StaminaAccess.get(player);
        if (magic.stamina() < STAMINA_COST_PER_HASTEN) {
            return;
        }
        StaminaAccess.set(player, magic.withStamina(magic.stamina() - STAMINA_COST_PER_HASTEN));
        long now = player.level().getGameTime();
        pending.hasten(TICKS_REMOVED_PER_HASTEN, now);

        // Tell the client about the new, shorter total duration - the
        // client's beam animation has no other way to know hastening
        // happened at all, and would otherwise keep animating at the
        // ORIGINAL duration while the server resolves early, visually
        // cutting the beam off before it ever reaches the target.
        // pending.durationTicks() is already "total duration measured
        // from the original start tick" (see PendingContact.hasten()),
        // exactly what the client needs to speed up smoothly rather than
        // restart from 0 - see ContactBeamState.adjustDuration().
        var targetEntity = com.dragonspeech.mind.EntityLookup.byUUID(player.getServer(), pending.targetId());
        if (targetEntity != null) {
            com.dragonspeech.network.DragonSpeechNetworking.sendContactBeam(player, targetEntity.getId(), (int) pending.durationTicks(), false);
        }
    }
}
