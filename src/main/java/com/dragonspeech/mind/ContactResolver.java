package com.dragonspeech.mind;

import com.dragonspeech.growth.AttunementAccess;
import com.dragonspeech.network.DragonSpeechNetworking;
import com.dragonspeech.storage.SkillsAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Resolves "reaching out with your mind" - Phase 6's foundational rule
 * that this is a SKILL for players (PlayerSkills.canReachOut), never
 * something spoken.
 *
 * Reaching out is NOT instant: it takes real travel time, faster the
 * more the attacker has mastered the skill (see durationTicks()), and
 * can be further hastened mid-reach by spending Stamina (see
 * PendingContactManager.hasten(), driven by HastenContactPayload). This
 * is what ContactBeamPayload exists for - the client renders an actual
 * traveling line toward the target for that whole duration (see
 * ContactBeamRenderer), matching the same "trail" visual language the
 * design calls for at every mastery level, not just the detection one.
 *
 * attempt()/attemptAsMob() only ever GATE and START a PendingContact;
 * the actual roll + duel creation happens in resolveReady(), called
 * once the travel time elapses (see PendingContactTicker).
 */
public final class ContactResolver {

    private static final Random RANDOM = new Random();
    private static final long COOLDOWN_TICKS = 20L * 8;
    private static final long BASE_DURATION_TICKS = 60L; // 3 seconds at zero mastery
    private static final long MIN_DURATION_TICKS = 10L;  // 0.5 seconds at full mastery
    private static final long MOB_DURATION_TICKS = 20L;  // mobs don't get a client beam to watch, so a flat short delay is enough

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private ContactResolver() {}

    public record ContactOutcome(boolean pending, boolean success, boolean onCooldown, boolean defenderNoticed, String message) {
        public static ContactOutcome gateFailed(String message) {
            return new ContactOutcome(false, false, false, false, message);
        }
        public static ContactOutcome onCooldown(String message) {
            return new ContactOutcome(false, false, true, false, message);
        }
        public static ContactOutcome started(String message) {
            return new ContactOutcome(true, false, false, false, message);
        }
    }

    /** Player-initiated - the ordinary keybind path. Starts a PendingContact and returns immediately; the actual outcome comes later via chat message + (on success) the duel screen opening. */
    public static ContactOutcome attempt(ServerPlayer attacker, LivingEntity target) {
        if (!SkillsAccess.get(attacker).canReachOut()) {
            return ContactOutcome.gateFailed("You have never learned to reach out with your mind. Knowing the words is not enough.");
        }
        // Config GUI (Server tab) "Allow PvP Mind Duels" - gates the duel from ever starting against
        // another real player at all. Mob targets are never affected by this setting.
        if (target instanceof ServerPlayer && !com.dragonspeech.config.DragonSpeechConfig.allowPvpMindDuels()) {
            return ContactOutcome.gateFailed("Reaching into another player's mind is disabled on this server.");
        }
        Optional<ContactOutcome> gateFailure = checkGates(attacker.getUUID(), target, attacker.level().getGameTime());
        if (gateFailure.isPresent()) {
            return gateFailure.get();
        }

        float attunement = AttunementAccess.get(attacker).get(com.dragonspeech.word.Domain.MIND);
        long duration = Math.max(MIN_DURATION_TICKS, Math.round(BASE_DURATION_TICKS - attunement * 0.5f));
        return start(attacker.getUUID(), target, duration, attacker.level().getGameTime());
    }

    /** Mob-initiated - no PlayerSkills gate (mobs have none); capability comes from SentienceTier.canActInDuel() instead. No client beam is sent (there's no player UI to show it to on the attacking side). */
    public static ContactOutcome attemptAsMob(LivingEntity mobAttacker, ServerPlayer target) {
        if (mobAttacker instanceof ServerPlayer) {
            return ContactOutcome.gateFailed("Players must use the ordinary Contact path.");
        }
        if (!MindFortitudeService.classifyTier(mobAttacker).canActInDuel()) {
            return ContactOutcome.gateFailed("This mind is not capable of reaching out on its own.");
        }
        Optional<ContactOutcome> gateFailure = checkGates(mobAttacker.getUUID(), target, mobAttacker.level().getGameTime());
        if (gateFailure.isPresent()) {
            return gateFailure.get();
        }
        return start(mobAttacker.getUUID(), target, MOB_DURATION_TICKS, mobAttacker.level().getGameTime());
    }

    private static Optional<ContactOutcome> checkGates(UUID attackerId, LivingEntity target, long now) {
        if (MindDuelManager.isInDuel(attackerId) || TeamMindDuelManager.isInDuel(attackerId) || PendingContactManager.isPending(attackerId)) {
            return Optional.of(ContactOutcome.gateFailed("Your mind is already engaged elsewhere."));
        }
        if (MindDuelManager.isInDuel(target.getUUID()) || TeamMindDuelManager.isInDuel(target.getUUID())) {
            return Optional.of(ContactOutcome.gateFailed("Their mind is already engaged elsewhere."));
        }
        Long cooldownUntil = COOLDOWN_UNTIL.get(attackerId);
        if (cooldownUntil != null && now < cooldownUntil) {
            return Optional.of(ContactOutcome.onCooldown("Your mind is still recovering from the last attempt."));
        }
        if (!(target instanceof ServerPlayer) && !MindFortitudeService.classifyTier(target).canBeDueled()) {
            return Optional.of(ContactOutcome.gateFailed("There is nothing here to reach."));
        }
        return Optional.empty();
    }

    private static ContactOutcome start(UUID attackerId, LivingEntity target, long durationTicks, long now) {
        PendingContactManager.start(new PendingContact(attackerId, target.getUUID(), now, durationTicks));
        ServerPlayer attackerPlayer = target.getServer() != null ? target.getServer().getPlayerList().getPlayer(attackerId) : null;
        if (attackerPlayer != null) {
            DragonSpeechNetworking.sendContactBeam(attackerPlayer, target.getId(), (int) durationTicks, false);
        }
        return ContactOutcome.started("You reach out with your mind, a single thread stretching toward them.");
    }

    /**
     * Instantly connects to a mind whose true name the attacker already
     * knows - no travel time, no barrier, straight to the same
     * permanent-mode options TRUE_NAME_DOMINATION grants after winning
     * one the normal way. This is what speaking a KNOWN name (in chat or
     * from the grimoire) does - per the design, knowing a true name
     * makes the whole contest moot, every time, not just the first time.
     * Skips every other gate attempt() has (cooldown, "already in duel,"
     * team-link handling) except the ones that are still meaningful here
     * (can't double-connect, target must actually be present).
     */
    public static ContactOutcome instantConnectViaTrueName(MinecraftServer server, ServerPlayer attacker, LivingEntity target) {
        if (MindDuelManager.isInDuel(attacker.getUUID()) || TeamMindDuelManager.isInDuel(attacker.getUUID())) {
            return ContactOutcome.gateFailed("Your mind is already engaged elsewhere.");
        }
        if (MindDuelManager.isInDuel(target.getUUID()) || TeamMindDuelManager.isInDuel(target.getUUID())) {
            return ContactOutcome.gateFailed("Their mind is already engaged elsewhere.");
        }

        MindCombatant attackerCombatant = MindFortitudeService.buildCombatant(attacker);
        MindCombatant defenderCombatant = MindFortitudeService.buildCombatant(target);
        SentienceTier defenderTier = target instanceof ServerPlayer ? SentienceTier.SIMPLE : MindFortitudeService.classifyTier(target);

        ActiveMindDuel duel = MindDuelManager.start(
            new ActiveMindDuel(attacker.getUUID(), target.getUUID(), attackerCombatant, defenderCombatant, defenderTier));
        duel.setTrueNameKnown(true);
        duel.setPhase(DuelPhase.TRUE_NAME_DOMINATION);
        duel.touch(server.overworld().getGameTime());

        attacker.sendSystemMessage(Component.literal("You speak their true name, and their mind opens to you completely - there is nothing left to fight through."));
        if (target instanceof ServerPlayer defenderPlayer) {
            defenderPlayer.sendSystemMessage(Component.literal("Your true name is spoken, and your mind lies open before them entirely."));
        }
        MindDuelSyncHooks.pushSync(server, duel);
        return ContactOutcome.started("Connected.");
    }

    // ---------------------------------------------------------------- resolution, called once travel time elapses (see PendingContactTicker)

    public static void resolveReady(MinecraftServer server, PendingContact contact) {
        PendingContactManager.clear(contact.attackerId());
        ServerPlayer attackerPlayer = server.getPlayerList().getPlayer(contact.attackerId());
        var targetEntity = EntityLookup.byUUID(server, contact.targetId());

        if (attackerPlayer != null) {
            DragonSpeechNetworking.sendContactBeam(attackerPlayer, 0, 0, true); // tells the client to stop rendering the beam
        }
        if (!(targetEntity instanceof LivingEntity target)) {
            if (attackerPlayer != null) {
                attackerPlayer.sendSystemMessage(Component.literal("The thread finds nothing at the other end - they are gone."));
            }
            return;
        }
        if (MindDuelManager.isInDuel(contact.attackerId()) || TeamMindDuelManager.isInDuel(contact.attackerId())
            || MindDuelManager.isInDuel(target.getUUID()) || TeamMindDuelManager.isInDuel(target.getUUID())) {
            if (attackerPlayer != null) {
                attackerPlayer.sendSystemMessage(Component.literal("The thread arrives too late - one of you is already engaged elsewhere."));
            }
            return;
        }

        Consumer<Component> messenger = attackerPlayer != null ? attackerPlayer::sendSystemMessage : component -> {};
        int attackerPower = attackerPlayer != null ? playerAttackerPower(attackerPlayer) : MindFortitudeService.fortitude(
            EntityLookup.byUUID(server, contact.attackerId()) instanceof LivingEntity le ? le : target);

        // A linked team is contacted as a whole, not one member at a time.
        if (target instanceof ServerPlayer targetPlayer) {
            Optional<MindLink> link = MindLinkManager.get(targetPlayer.getUUID());
            if (link.isPresent() && link.get().memberIds().size() > 1) {
                resolveTeam(server, contact.attackerId(), link.get(), attackerPower, messenger, server.overworld().getGameTime());
                return;
            }
        }

        // No success/failure roll any more - the thread is purely
        // visual, representing "reaching out with your mind," not a
        // contested attempt that can simply fail. Reaching the target
        // always makes Contact.
        SentienceTier tier = target instanceof ServerPlayer ? null : MindFortitudeService.classifyTier(target);
        COOLDOWN_UNTIL.put(contact.attackerId(), server.overworld().getGameTime() + COOLDOWN_TICKS);

        var attackerEntity = EntityLookup.byUUID(server, contact.attackerId());
        if (!(attackerEntity instanceof LivingEntity livingAttacker)) {
            return;
        }
        MindCombatant attackerCombatant = MindFortitudeService.buildCombatant(livingAttacker);
        MindCombatant defenderCombatant = MindFortitudeService.buildCombatant(target);
        SentienceTier defenderTier = tier != null ? tier : SentienceTier.SIMPLE;

        ActiveMindDuel duel = MindDuelManager.start(
            new ActiveMindDuel(contact.attackerId(), target.getUUID(), attackerCombatant, defenderCombatant, defenderTier));
        duel.touch(server.overworld().getGameTime());

        messenger.accept(Component.literal("Contact! The thread connects. You are inside the outer edge of their mind - now you must break through."));
        if (target instanceof ServerPlayer defenderPlayer) {
            defenderPlayer.sendSystemMessage(Component.literal("A mind presses against yours, seeking a way in!"));
        }
        MindDuelSyncHooks.pushSync(server, duel);
    }

    private static void resolveTeam(MinecraftServer server, UUID attackerId, MindLink link, int attackerPower, Consumer<Component> attackerMessenger, long now) {
        List<ServerPlayer> members = new ArrayList<>();
        for (UUID memberId : link.memberIds()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member == null || MindDuelManager.isInDuel(memberId) || TeamMindDuelManager.isInDuel(memberId)) {
                attackerMessenger.accept(Component.literal("One of them is not able to be reached right now - the link cannot be engaged."));
                return;
            }
            members.add(member);
        }

        COOLDOWN_UNTIL.put(attackerId, now + COOLDOWN_TICKS);

        var attackerEntity = EntityLookup.byUUID(server, attackerId);
        if (!(attackerEntity instanceof LivingEntity livingAttacker)) {
            return;
        }
        MindCombatant attackerCombatant = MindFortitudeService.buildCombatant(livingAttacker);
        Map<UUID, MindCombatant> defenderCombatants = new LinkedHashMap<>();
        for (ServerPlayer member : members) {
            defenderCombatants.put(member.getUUID(), MindFortitudeService.buildCombatant(member));
        }

        TeamMindDuel duel = TeamMindDuelManager.start(new TeamMindDuel(attackerId, attackerCombatant, link.linkId(), defenderCombatants));
        duel.touch(now);

        attackerMessenger.accept(Component.literal("Contact! The thread connects to the whole of their link at once - " + members.size() + " minds, bound together."));
        for (ServerPlayer member : members) {
            member.sendSystemMessage(Component.literal("A mind presses against your shared link, seeking a way in - the whole bond is under attack!"));
        }
        TeamMindDuelSyncHooks.pushSync(server, duel);
    }

    private static int playerAttackerPower(ServerPlayer attacker) {
        float attunement = AttunementAccess.get(attacker).get(com.dragonspeech.word.Domain.MIND);
        int base = 10;
        return base + Math.round(attunement * 0.6f);
    }
}
