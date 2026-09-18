package com.dragonspeech.mind;

import com.dragonspeech.network.DragonSpeechNetworking;
import com.dragonspeech.network.MindControlInputPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Short lived occupied-mind body control. The attacker's camera attaches
 * to the target while their client forwards live movement input to the
 * server. The attacker's original body location is restored when the
 * control ends.
 */
public final class MindControlService {

    private static final Random RANDOM = new Random();
    private static final Map<UUID, ControlLink> LINKS_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Long> LAST_ATTACK_BY_TARGET = new HashMap<>();
    private static final Map<UUID, Long> LAST_USE_BY_TARGET = new HashMap<>();
    private static final Map<UUID, float[]> LAST_LOOK_BY_TARGET = new HashMap<>();
    private static final long SKILL_CHECK_INTERVAL_TICKS = 20L * 10L;
    private static final long ACTION_COOLDOWN_TICKS = 4L;
    private static final double WALK_SPEED = 0.24;
    private static final double SNEAK_SPEED = 0.12;
    private static final double REACH = 4.5;
    /** Minimum alignment (cosine) with the look direction for an entity to count as "looked at" - roughly a 40-degree cone, tight enough to feel like real aiming, not a wide net. */
    private static final double ATTACK_CONE_COSINE = 0.75;

    private record ControlLink(
        UUID attackerId,
        UUID targetId,
        long untilGameTime,
        long nextSkillCheckGameTime,
        ResourceKey<Level> attackerDimension,
        Vec3 attackerPosition,
        float attackerYRot,
        float attackerXRot,
        boolean permanent
    ) {}

    private MindControlService() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MindControlService::tick);
    }

    public static void start(MinecraftServer server, LivingEntity attacker, LivingEntity target, long durationTicks) {
        long now = server.overworld().getGameTime();
        boolean permanent = MindDuelManager.forParticipant(target.getUUID())
            .map(ActiveMindDuel::trueNameKnown)
            .orElse(false);
        // The attacker's real body stays exactly as vulnerable as normal
        // while controlling - no invincibility. If it takes damage, the
        // connection is severed outright (see onPhysicalDamage(), called
        // from LivingEntityDamageMixin) rather than being made
        // untouchable in the first place.
        // The mob's own AI (goal selector) has to be turned off entirely
        // while controlled - otherwise it keeps running autonomously
        // every tick (wandering, targeting, fleeing) and fights the
        // player's actual input regardless of skill-check mode. This is
        // a completely different thing from the deliberate periodic
        // resistance check (which is the ONLY "fighting back" that
        // should exist, and only in temporary/non-permanent mode) - an
        // uncontrolled AI still running underneath was just noise on top
        // of that, in both modes.
        // NOT setNoAi(true) - that's the well-known "frozen mob statue"
        // NBT flag (NoAI:1b), and it freezes the entity's physics
        // entirely, not just its own decision-making. Repeatedly
        // stopping navigation instead suppresses most AI-driven movement
        // (pathfinding-based goals, which is how the large majority of
        // vanilla mob movement works) without touching the entity's own
        // velocity/physics, so player-driven input still actually moves
        // it. See tick(), where this gets called every tick, not just
        // once here - a single call wouldn't stop a goal from re-issuing
        // a new path on a later tick.
        if (target instanceof Mob controlledMob) {
            controlledMob.getNavigation().stop();
        }
        LINKS_BY_TARGET.put(target.getUUID(), new ControlLink(attacker.getUUID(), target.getUUID(),
            now + durationTicks, now + SKILL_CHECK_INTERVAL_TICKS, attacker.level().dimension(), attacker.position(),
            attacker.getYRot(), attacker.getXRot(), permanent));
        if (attacker instanceof ServerPlayer player) {
            player.setCamera(target);
            DragonSpeechNetworking.sendMindControlState(player, target.getId(), true);
        }
    }

    public static boolean isControlled(UUID targetId) {
        return LINKS_BY_TARGET.containsKey(targetId);
    }

    public static void handleInput(ServerPlayer attacker, MindControlInputPayload payload) {
        MinecraftServer server = attacker.getServer();
        if (server == null) {
            return;
        }
        ControlLink link = linkForAttacker(attacker.getUUID());
        if (link == null) {
            return;
        }
        var targetRaw = EntityLookup.byUUID(server, link.targetId());
        if (!(targetRaw instanceof LivingEntity target) || !target.isAlive()) {
            endControl(server, link);
            return;
        }

        applyLook(target, payload.yaw(), payload.pitch());
        LAST_LOOK_BY_TARGET.put(target.getUUID(), new float[] {payload.yaw(), payload.pitch()});
        applyMovement(target, payload);
        applyActions(server, target, payload);
        lockAttackerBody(attacker, link.attackerPosition());
    }

    public static DuelActionResult resist(MinecraftServer server, ActiveMindDuel duel) {
        ControlLink link = LINKS_BY_TARGET.get(duel.defenderId());
        if (link == null) {
            return DuelActionResult.illegal("There is no active control to resist.");
        }
        if (link.permanent()) {
            return DuelActionResult.illegal("Your true name is known - there is nothing left here to fight against.");
        }
        var defenderRaw = EntityLookup.byUUID(server, duel.defenderId());
        if (!(defenderRaw instanceof LivingEntity defender)) {
            endControl(server, link);
            return DuelActionResult.illegal("There is no body here to control.");
        }
        if (passesSkillCheck(defender, link)) {
            endControl(server, link);
            MindDuelService.end(server, duel, DuelOutcome.DEFENDER_VICTORY, "The defender breaks the control and severs the connection.");
            return DuelActionResult.ended("You break their control and sever the connection.", DuelOutcome.DEFENDER_VICTORY);
        }
        duel.defender().drainStamina(6f);
        return DuelActionResult.ok("You fight the control, but it holds. Your mind stamina strains.");
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, ControlLink>> iterator = LINKS_BY_TARGET.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ControlLink> entry = iterator.next();
            ControlLink link = entry.getValue();
            if (!link.permanent() && now >= link.untilGameTime()) {
                restoreControl(server, link);
                iterator.remove();
                continue;
            }

            var attackerRaw = EntityLookup.byUUID(server, link.attackerId());
            var targetRaw = EntityLookup.byUUID(server, link.targetId());
            if (!(attackerRaw instanceof LivingEntity attacker) || !(targetRaw instanceof LivingEntity target) || !target.isAlive()) {
                restoreControl(server, link);
                iterator.remove();
                continue;
            }
            if (target instanceof Mob controlledMob) {
                controlledMob.getNavigation().stop();
            }
            // Re-assert the last known look direction every tick, not just
            // when a fresh input packet arrives - the mob's own head-
            // tracking (LookControl) keeps ticking normally underneath
            // Control and can nudge rotation in the gap between packets,
            // which reads as jitter. Closing that gap here, the same way
            // the camera gets re-asserted below, is the fix for the
            // reported "constant up and down" fighting.
            float[] lastLook = LAST_LOOK_BY_TARGET.get(link.targetId());
            if (lastLook != null) {
                applyLook(target, lastLook[0], lastLook[1]);
            }

            // Permanent mode (true name known) never rolls a periodic
            // skill check at all - control simply holds until the
            // attacker disengages.
            if (!link.permanent() && now >= link.nextSkillCheckGameTime()) {
                if (passesSkillCheck(target, link)) {
                    message(target, "You wrench your body free of the invading will.");
                    message(attacker, "They break your control.");
                    restoreControl(server, link);
                    iterator.remove();
                    ActiveMindDuel duel = MindDuelManager.forParticipant(link.targetId()).orElse(null);
                    if (duel != null) {
                        MindDuelService.end(server, duel, DuelOutcome.DEFENDER_VICTORY, "The defender breaks the control and severs the connection.");
                    }
                    continue;
                }
                message(target, "You fight the invading will, but it holds.");
                entry.setValue(new ControlLink(link.attackerId(), link.targetId(), link.untilGameTime(),
                    now + SKILL_CHECK_INTERVAL_TICKS, link.attackerDimension(), link.attackerPosition(),
                    link.attackerYRot(), link.attackerXRot(), link.permanent()));
            }

            lockAttackerBody(attacker, link.attackerPosition());
            // Re-assert the camera every tick rather than trusting the
            // one-time setCamera() from start() to hold - vanilla's own
            // flight-adjacent controls (Jump, Sprint) can knock the
            // camera-follow loose if the player's client ever processes
            // those keys for their real entity. Re-asserting here
            // overrides that every single tick, so any such glitch never
            // lasts more than an instant.
            if (attacker instanceof ServerPlayer attackerPlayer && attackerPlayer.getCamera() != target) {
                attackerPlayer.setCamera(target);
            }
        }
    }

    /** Ends Control on this target, if any is active - safe to call unconditionally from anywhere a duel might end (Disengage, a resistance win, timeout, etc.), not just from Control's own internal logic. A no-op if nothing is controlling this target. */
    public static void endControlForTarget(MinecraftServer server, UUID targetId) {
        ControlLink link = LINKS_BY_TARGET.get(targetId);
        if (link != null) {
            endControl(server, link);
        }
    }

    private static void endControl(MinecraftServer server, ControlLink link) {
        LINKS_BY_TARGET.remove(link.targetId());
        restoreControl(server, link);
    }

    private static void restoreControl(MinecraftServer server, ControlLink link) {
        LAST_ATTACK_BY_TARGET.remove(link.targetId());
        LAST_USE_BY_TARGET.remove(link.targetId());
        LAST_LOOK_BY_TARGET.remove(link.targetId());
        var attackerRaw = EntityLookup.byUUID(server, link.attackerId());
        if (attackerRaw instanceof ServerPlayer player) {
            player.setCamera(player);
            DragonSpeechNetworking.sendMindControlState(player, -1, false);
            ServerLevel originalLevel = server.getLevel(link.attackerDimension());
            if (originalLevel != null) {
                player.teleportTo(originalLevel, link.attackerPosition().x, link.attackerPosition().y, link.attackerPosition().z,
                    link.attackerYRot(), link.attackerXRot());
            }
        }
    }

    private static ControlLink linkForAttacker(UUID attackerId) {
        for (ControlLink link : LINKS_BY_TARGET.values()) {
            if (link.attackerId().equals(attackerId)) {
                return link;
            }
        }
        return null;
    }

    /**
     * Called from LivingEntityDamageMixin for any living entity taking
     * damage. If this entity is currently possessing something via
     * Control, that connection is severed immediately - the attacker's
     * real body is deliberately left just as vulnerable as normal while
     * controlling (no invincibility), and getting hurt is the actual
     * consequence of that, rather than something prevented outright.
     */
    public static void onPhysicalDamage(MinecraftServer server, LivingEntity entity) {
        ControlLink link = linkForAttacker(entity.getUUID());
        if (link == null) {
            return;
        }
        endControl(server, link);
        ActiveMindDuel duel = MindDuelManager.forParticipant(link.targetId()).orElse(null);
        if (duel != null) {
            MindDuelService.end(server, duel, DuelOutcome.DEFENDER_VICTORY,
                "A blow lands on the attacker's own body, and the mental grip on the controlled mind is lost entirely.");
        } else if (entity instanceof ServerPlayer attackerPlayer) {
            attackerPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Pain jolts you back into your own body - your control over their mind is broken."));
        }
    }

    private static boolean passesSkillCheck(LivingEntity target, ControlLink link) {
        ActiveMindDuel duel = MindDuelManager.forParticipant(link.targetId()).orElse(null);
        MindCombatant defender = duel != null && duel.defenderId().equals(link.targetId()) ? duel.defender() : null;
        float chance;
        if (defender != null) {
            chance = ResistanceCheck.resistChance(defender, 18f, duel.phase() == DuelPhase.TRUE_NAME_DOMINATION);
        } else {
            chance = Math.min(85f, Math.max(5f, MindFortitudeService.fortitude(target)));
        }
        return RANDOM.nextFloat() * 100f < chance;
    }

    private static void applyLook(LivingEntity target, float yaw, float pitch) {
        target.setYRot(yaw);
        target.setYHeadRot(yaw);
        target.setXRot(Math.max(-90f, Math.min(90f, pitch)));
    }

    private static void applyMovement(LivingEntity target, MindControlInputPayload payload) {
        double forward = (payload.has(MindControlInputPayload.FORWARD) ? 1.0 : 0.0)
            - (payload.has(MindControlInputPayload.BACK) ? 1.0 : 0.0);
        double strafe = (payload.has(MindControlInputPayload.RIGHT) ? 1.0 : 0.0)
            - (payload.has(MindControlInputPayload.LEFT) ? 1.0 : 0.0);
        double yawRadians = Math.toRadians(payload.yaw());
        Vec3 forwardVector = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        // At yaw=0 (facing south, +Z), a player's physical right points
        // west (-X) - turning 180 from facing north swaps which
        // cardinal direction is on which hand. The old formula
        // (cos(yaw), sin(yaw)) pointed east at yaw=0 instead, exactly
        // backwards, which is what was swapping A and D.
        Vec3 rightVector = new Vec3(-Math.cos(yawRadians), 0.0, -Math.sin(yawRadians));
        Vec3 horizontal = forwardVector.scale(forward).add(rightVector.scale(strafe));
        if (horizontal.lengthSqr() > 1.0) {
            horizontal = horizontal.normalize();
        }

        double speed = payload.has(MindControlInputPayload.SNEAK) ? SNEAK_SPEED : WALK_SPEED;
        Vec3 current = target.getDeltaMovement();
        double y = current.y;
        if (payload.has(MindControlInputPayload.JUMP) && target.onGround()) {
            y = 0.42;
        }
        target.setDeltaMovement(horizontal.x * speed, y, horizontal.z * speed);
        target.hasImpulse = true;
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    private static void applyActions(MinecraftServer server, LivingEntity target, MindControlInputPayload payload) {
        long now = server.overworld().getGameTime();
        if (payload.has(MindControlInputPayload.ATTACK) && canAct(LAST_ATTACK_BY_TARGET, target.getUUID(), now)) {
            LAST_ATTACK_BY_TARGET.put(target.getUUID(), now);
            attackOrBreak(target);
        }
        if (payload.has(MindControlInputPayload.USE) && canAct(LAST_USE_BY_TARGET, target.getUUID(), now)) {
            LAST_USE_BY_TARGET.put(target.getUUID(), now);
            useOrPlace(target);
        }
    }

    /** Right-click equivalent - places a block/uses the held item, via the exact same vanilla gameMode entry point a real client interaction goes through. Mobs have no meaningful "use" of their own, so this only does anything for a controlled player. */
    private static void useOrPlace(LivingEntity target) {
        if (!(target instanceof ServerPlayer controlledPlayer)) {
            return;
        }
        Vec3 look = target.getLookAngle();
        Vec3 start = target.getEyePosition(1.0f);
        Vec3 end = start.add(look.scale(REACH));
        HitResult hit = target.level().clip(new ClipContext(
            start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, target
        ));
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHitResult) {
            for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
                var result = controlledPlayer.gameMode.useItemOn(controlledPlayer, controlledPlayer.level(), controlledPlayer.getItemInHand(hand), hand, blockHitResult);
                if (result.consumesAction()) {
                    return;
                }
            }
        }
    }

    private static boolean canAct(Map<UUID, Long> lastAction, UUID targetId, long now) {
        return now - lastAction.getOrDefault(targetId, -ACTION_COOLDOWN_TICKS) >= ACTION_COOLDOWN_TICKS;
    }

    private static void attackOrBreak(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 start = target.getEyePosition(1.0f);
        Vec3 end = start.add(look.scale(REACH));

        Entity entityHit = findLookedAtEntity(target, start, look);
        if (entityHit != null) {
            if (target instanceof ServerPlayer controlledPlayer) {
                controlledPlayer.attack(entityHit);
            } else if (target instanceof Mob mob) {
                mob.doHurtTarget(entityHit);
            }
            return;
        }

        if (target instanceof ServerPlayer controlledPlayer) {
            HitResult blockHit = target.level().clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, target
            ));
            if (blockHit.getType() == HitResult.Type.BLOCK && blockHit instanceof BlockHitResult blockHitResult) {
                controlledPlayer.gameMode.destroyBlock(blockHitResult.getBlockPos());
            }
        }
    }

    /**
     * Manual entity raycast, replacing an earlier ProjectileUtil-based
     * version whose exact runtime behavior for this use case couldn't be
     * verified without a compiler - and which, per the report that block-
     * breaking worked but attacking never did, was almost certainly the
     * actual reason attacking never worked. Same proven technique
     * DragonSpeechClient's findReachTarget already uses successfully:
     * scan nearby LivingEntities, keep only ones within reach AND roughly
     * in front (cosine alignment with the look direction), pick whichever
     * is most directly aligned.
     */
    private static Entity findLookedAtEntity(LivingEntity target, Vec3 eyePos, Vec3 look) {
        Entity best = null;
        double bestCosine = ATTACK_CONE_COSINE;

        for (LivingEntity candidate : target.level().getEntitiesOfClass(LivingEntity.class,
            target.getBoundingBox().inflate(REACH), e -> e != target && e.isAlive() && e.isPickable())) {
            Vec3 toCandidate = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(eyePos);
            double along = toCandidate.dot(look);
            if (along <= 0 || along > REACH) {
                continue;
            }
            double cosine = toCandidate.normalize().dot(look);
            if (cosine > bestCosine) {
                bestCosine = cosine;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Holds the attacker's real body exactly at attackerAnchor (their
     * position when Control began) every tick - not just zeroed
     * velocity. This is the fix for "body disappears / merges with the
     * mob": zeroing velocity alone only stops FUTURE drift, it can't
     * undo movement already integrated into position during the SAME
     * tick before this runs. The leading theory for why the attacker's
     * real body was moving at all: nothing suppresses vanilla's own
     * normal WASD-to-movement handling for the attacker's own local
     * player entity - MindControlClientState only ever ADDS a custom
     * input payload on top of the unmodified vanilla pipeline, it
     * doesn't replace it, so the attacker's real body could still be
     * walking/stepping normally from the exact same key presses driving
     * the controlled target. Hard-anchoring closes that regardless of
     * the precise mechanism, the same way the defender lock does.
     */
    private static void lockAttackerBody(LivingEntity attacker, Vec3 attackerAnchor) {
        if (!(attacker instanceof ServerPlayer)) {
            return;
        }
        Vec3 current = attacker.position();
        double dx = current.x - attackerAnchor.x, dz = current.z - attackerAnchor.z;
        if (dx * dx + dz * dz > 0.01) {
            attacker.teleportTo(attackerAnchor.x, current.y, attackerAnchor.z);
        }
        attacker.setDeltaMovement(0.0, attacker.getDeltaMovement().y, 0.0);
        attacker.hasImpulse = true;
    }

    private static void message(LivingEntity entity, String text) {
        if (entity instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal(text));
        }
    }
}
