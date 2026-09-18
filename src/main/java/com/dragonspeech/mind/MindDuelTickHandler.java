package com.dragonspeech.mind;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Jobs on two different cadences:
 *
 * 1. EVERY tick: the defender (and every team-duel defender) is held in
 *    place for the whole duration of the duel - Defense Breach through
 *    to however it resolves - not just while Control is active. Needs
 *    real per-tick precision (a once-a-second correction would let them
 *    visibly drift for up to a full second between corrections), unlike
 *    everything else here.
 *
 * 2. Once a second, mirroring StaminaTicker:
 *    a. Passive regen for all five bars (Focus, Stamina, Willpower,
 *       Power, Speed) on both sides of every open duel - each bar's own
 *       regen rate was set once at duel start (see MindFortitudeService)
 *       and scales with how strong the combatant is with the mind skill.
 *    b. Abandonment timeout - if neither side has taken an action in a
 *       while (a player alt-F4'd, disconnected, or the client just never
 *       sent an action), the duel is force-ended instead of sitting open
 *       forever and blocking both participants from starting a new one.
 */
public final class MindDuelTickHandler {

    private static final int PULSE_INTERVAL_TICKS = 20;
    private static final float SECONDS_PER_PULSE = 1.0f;
    private static final long ABANDON_TIMEOUT_TICKS = 20L * 60 * 3; // 3 minutes of silence
    /** How far (in blocks, squared) a locked defender is allowed to drift horizontally before being snapped back to their anchor. Small enough to catch jump-momentum creep, large enough to not fight normal knockback/physics jitter. */
    private static final double DRIFT_TOLERANCE_SQR = 0.01;

    private static int counter = 0;
    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> DEFENDER_ANCHORS = new java.util.HashMap<>();

    private MindDuelTickHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MindDuelTickHandler::tick);
    }

    private static void tick(MinecraftServer server) {
        lockDefenders(server);

        counter++;
        if (counter < PULSE_INTERVAL_TICKS) {
            return;
        }
        counter = 0;

        long now = server.overworld().getGameTime();
        boolean anyChanged = false;
        for (ActiveMindDuel duel : MindDuelManager.allActive()) {
            if (now - duel.lastActionGameTime() > ABANDON_TIMEOUT_TICKS) {
                MindDuelService.end(server, duel, DuelOutcome.INTERRUPTED, "The connection between both minds simply fades from neglect.");
                continue;
            }
            duel.attacker().regenTick(SECONDS_PER_PULSE);
            duel.defender().regenTick(SECONDS_PER_PULSE);
            anyChanged = true;
        }
        if (anyChanged) {
            for (ActiveMindDuel duel : MindDuelManager.allActive()) {
                MindDuelSyncHooks.pushSync(server, duel);
            }
        }

        for (TeamMindDuel duel : TeamMindDuelManager.allActive()) {
            if (now - duel.lastActionGameTime() > ABANDON_TIMEOUT_TICKS) {
                TeamMindDuelService.end(server, duel, DuelOutcome.INTERRUPTED, "The link simply fades from neglect - no one is holding the connection any more.");
                continue;
            }
            duel.attacker().regenTick(SECONDS_PER_PULSE);
            for (MindCombatant member : duel.defenders().values()) {
                member.regenTick(SECONDS_PER_PULSE);
            }
        }
    }

    /**
     * Holds every current defender in place - the whole duel, not just
     * while Control is active. Rotation/looking is deliberately left
     * alone, same reasoning as lockAttackerBody - a frozen mind, not a
     * frozen camera. Skips a defender already under Control, since
     * MindControlService's own per-tick handling already governs their
     * movement entirely in that case.
     *
     * Zeroing velocity alone (the original approach) only prevents
     * FUTURE ticks from continuing to drift - it can't undo movement a
     * one-time impulse (a jump goal's forward-leaning momentum, in
     * particular) already integrated into position THIS tick, before
     * this method ever runs. Repeated jumping was accumulating small net
     * forward steps that slipped through before the zero-out applied.
     * Anchoring X/Z hard to the position they were first locked at (set
     * once, lazily, the first tick a defender is observed) closes that
     * gap completely - Y is deliberately left alone so gravity/landing
     * still behaves naturally, only horizontal escape is prevented.
     */
    private static void lockDefenders(MinecraftServer server) {
        java.util.Set<java.util.UUID> stillLocked = new java.util.HashSet<>();

        for (ActiveMindDuel duel : MindDuelManager.allActive()) {
            if (MindControlService.isControlled(duel.defenderId())) {
                continue;
            }
            var defenderRaw = EntityLookup.byUUID(server, duel.defenderId());
            if (defenderRaw instanceof net.minecraft.world.entity.LivingEntity defender) {
                anchorAndLock(defender);
                stillLocked.add(duel.defenderId());
            }
        }
        for (TeamMindDuel duel : TeamMindDuelManager.allActive()) {
            for (java.util.UUID memberId : duel.defenders().keySet()) {
                if (MindControlService.isControlled(memberId)) {
                    continue;
                }
                var memberRaw = EntityLookup.byUUID(server, memberId);
                if (memberRaw instanceof net.minecraft.world.entity.LivingEntity member) {
                    anchorAndLock(member);
                    stillLocked.add(memberId);
                }
            }
        }

        DEFENDER_ANCHORS.keySet().retainAll(stillLocked);
    }

    private static void anchorAndLock(net.minecraft.world.entity.LivingEntity defender) {
        net.minecraft.world.phys.Vec3 anchor = DEFENDER_ANCHORS.computeIfAbsent(defender.getUUID(), id -> defender.position());
        net.minecraft.world.phys.Vec3 current = defender.position();
        double dx = current.x - anchor.x, dz = current.z - anchor.z;
        if (dx * dx + dz * dz > DRIFT_TOLERANCE_SQR) {
            defender.teleportTo(anchor.x, current.y, anchor.z);
        }
        defender.setDeltaMovement(0.0, defender.getDeltaMovement().y, 0.0);
        defender.hasImpulse = true;
    }
}