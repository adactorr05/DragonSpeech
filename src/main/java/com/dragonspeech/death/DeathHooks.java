package com.dragonspeech.death;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Records deaths so a resurrection working has a thread to catch, and
 * checks the two forms of SELF pre-cast revival every time a player
 * dies, in priority order:
 *
 *  1. A persistent REVIVAL ward (from "aftrlifga sjalfan <a binding
 *     word>", cast anytime beforehand, lasts until consumed) - the
 *     ward-form.
 *  2. A short 5-second priming (from "aftrlifga sjalfan" alone, no
 *     binding word) - the timing-form. See SelfRevivalPrimedMarkers.
 *
 * Either one hands off to ReviveScheduler rather than respawning
 * immediately in this same event - see that class for why the short
 * delay matters. If neither applies, the death is simply recorded as an
 * ordinary thread for ANOTHER caster to reach for later (or, for mobs,
 * for a "aftrlifga nar/thetta" working to find).
 *
 * VERSION-RISK NOTE: ServerLivingEntityEvents.AFTER_DEATH is this file's
 * fabric-api touch point (entity-event api v1).
 */
public final class DeathHooks {

    private DeathHooks() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                handlePlayerDeath(player);
                return;
            }

            if (entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
                net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
                entity.save(saved);
                RecentDeaths.recordMob(new RecentDeaths.MobDeathRecord(
                    entity.getType(),
                    saved,
                    entity.position(),
                    level.dimension(),
                    level.getGameTime(),
                    com.dragonspeech.effect.KineticCost.massFactor(entity)
                ));
            }
        });
    }

    /**
     * BUG FIX: wards used to just sit in the player's persistent ward
     * data across a death, with nothing here ever touching them. In
     * practice that meant a dead-then-respawned player could see old
     * ward rings still rendered (the client's last-known sync never got
     * updated) while the wards themselves no longer actually blocked
     * anything - a "wards are visible but don't do anything" state that
     * was really a client/server desync, not a deliberate feature.
     *
     * The clean fix is to make death unconditionally break every ward
     * the caster was carrying - the one deliberate exception is the
     * REVIVAL ward, which isn't a shield at all; it's consumed FOR the
     * revival attempt below, not broken by the death it's meant to
     * prevent. Whichever path this method takes after, the wipe below
     * always runs first, and pushSync() tells the client immediately so
     * the ring visuals clear in the same moment, not on next login.
     */
    private static void handlePlayerDeath(ServerPlayer player) {
        long now = player.level().getGameTime();

        var wards = com.dragonspeech.ward.WardAccess.get(player);
        var revivalWard = wards.wards().stream()
            .filter(w -> w.type() == com.dragonspeech.ward.WardType.REVIVAL)
            .findFirst();

        com.dragonspeech.ward.WardAccess.set(player, com.dragonspeech.ward.PlayerWards.empty());
        com.dragonspeech.ward.WardService.pushSync(player);

        if (revivalWard.isPresent()) {
            ReviveScheduler.enqueue(player,
                "The binding you laid answers before the crossing can take you - and something is missing for it.");
            return;
        }

        if (SelfRevivalPrimedMarkers.consumeIfActive(player.getUUID(), now)) {
            ReviveScheduler.enqueue(player,
                "You catch your own thread in the instant it nearly slips - and something is missing for the effort.");
            return;
        }

        RecentDeaths.record(player.getUUID(), player.position(), player.level().dimension(), now);
    }
}
