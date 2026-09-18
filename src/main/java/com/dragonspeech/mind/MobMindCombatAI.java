package com.dragonspeech.mind;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The thing that makes sentient mobs actually FIGHT or DEFEND for
 * themselves in a mind duel, instead of just sitting there as a passive
 * stat pool for a player to whittle down. Every resolver in this system
 * was already actor-agnostic - they only ever check "does this UUID
 * match the attacker or defender," never "is this a ServerPlayer" - so
 * the only missing piece was something to actually CALL them on a
 * mob's behalf. This class is that something.
 *
 * Gated entirely on SentienceTier.canActInDuel(): out of the box that's
 * only Wardens and Endermen (CHAOTIC-tier), plus anything an addon
 * registers into MindFortitudeService.TIER_OVERRIDES at TRAINED or
 * above - a Dragon, once such a mod exists, would be DRAGON-tier and
 * (per clickAttemptsPerPulse below) the fastest and hardest mind to
 * break in the game.
 *
 * Defense Breach mobs "click" the same STRIKE_CRACK/SEAL_CRACK actions
 * a player does, picking a random card and a random crack each attempt
 * - but a mob's TIER decides how many attempts it gets per second (see
 * clickAttemptsPerPulse), which is the "own speed depending on its
 * tier" the design calls for. A Warden clicks faster than a barely-
 * trained mind; nothing clicks faster than a Dragon.
 */
public final class MobMindCombatAI {

    private static final Random RANDOM = new Random();
    private static final int ACT_PULSE_TICKS = 20; // 1 second
    private static final int INITIATE_PULSE_TICKS = 100;
    private static final float INITIATE_CHANCE_PER_CANDIDATE = 0.05f;
    private static final double INITIATE_RANGE = 10.0;
    /** Mobs are naturally cautious with their Focus bar too - it's exactly as dangerous for them as it is for a player. */
    private static final float MOB_FOCUS_CARD_WEIGHT = 1f;

    private static int actCounter = 0;
    private static int initiateCounter = 0;

    private MobMindCombatAI() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MobMindCombatAI::tick);
        // Addon extension point (see com.dragonspeech.api.MindDuelBrain#onDuelEnded) - forwarded
        // straight from the same END_LISTENERS mechanism DragonHeartService and friends already use,
        // so a brain's post-duel hook fires off the real, final DuelOutcome.
        MindDuelService.END_LISTENERS.add(MobMindCombatAI::notifyBrainDuelEnded);
    }

    private static void notifyBrainDuelEnded(MinecraftServer server, ActiveMindDuel duel, DuelOutcome outcome) {
        var brain = com.dragonspeech.api.MindDuelBrainRegistry.get();
        if (brain == null) {
            return;
        }
        notifyDuelEndIfHandled(brain, server, duel.attackerId(), outcome);
        notifyDuelEndIfHandled(brain, server, duel.defenderId(), outcome);
    }

    private static void notifyDuelEndIfHandled(com.dragonspeech.api.MindDuelBrain brain, MinecraftServer server, UUID entityId, DuelOutcome outcome) {
        var entity = EntityLookup.byUUID(server, entityId);
        if (!(entity instanceof LivingEntity living) || entity instanceof ServerPlayer) {
            return;
        }
        SentienceTier tier = MindFortitudeService.classifyTier(living);
        if (brain.handles(living, tier)) {
            brain.onDuelEnded(living, tier, outcome);
        }
    }

    private static void tick(MinecraftServer server) {
        actCounter++;
        if (actCounter >= ACT_PULSE_TICKS) {
            actCounter = 0;
            actTick(server);
        }
        initiateCounter++;
        if (initiateCounter >= INITIATE_PULSE_TICKS) {
            initiateCounter = 0;
            initiateTick(server);
        }
    }

    // ---------------------------------------------------------------- acting (defend or continue an attack)

    private static void actTick(MinecraftServer server) {
        // AI compute budget (config GUI, Server tab) - a server-wide cap on how many actor-decisions
        // run per pulse, applying equally to the built-in heuristic AND any registered addon brain
        // (both go through the exact same maybeAct1v1/maybeActTeamAttacker call sites below) - see
        // DragonSpeechConfig#aiComputeBudget()'s own doc for why this is enforced centrally here
        // rather than left for each addon to remember to respect on its own. 0 = unlimited.
        int budget = com.dragonspeech.config.DragonSpeechConfig.aiComputeBudget();
        int processed = 0;
        for (ActiveMindDuel duel : MindDuelManager.allActive()) {
            if (budget > 0 && processed >= budget) {
                return;
            }
            if (duel.phase() == DuelPhase.CONTACT || duel.phase() == DuelPhase.ENDED || duel.pendingRoleChoice()) {
                continue;
            }
            maybeAct1v1(server, duel, duel.defenderId(), false);
            processed++;
            if (budget > 0 && processed >= budget) {
                return;
            }
            maybeAct1v1(server, duel, duel.attackerId(), true);
            processed++;
        }
        for (TeamMindDuel duel : TeamMindDuelManager.allActive()) {
            if (budget > 0 && processed >= budget) {
                return;
            }
            if (duel.ended()) {
                continue;
            }
            maybeActTeamAttacker(server, duel, duel.attackerId());
            processed++;
        }
    }

    private static void maybeAct1v1(MinecraftServer server, ActiveMindDuel duel, UUID actorId, boolean actorIsAttacker) {
        var entity = EntityLookup.byUUID(server, actorId);
        SentienceTier tier = mobTierIfCapable(entity);
        if (tier == null) {
            return;
        }
        // FIX: "Entity cannot be converted to LivingEntity" - EntityLookup.byUUID() returns the
        // broader Entity type, but mobTierIfCapable() already returning a non-null tier means its OWN
        // internal "entity instanceof LivingEntity" check just passed - this cast is always safe at
        // this point, it's only narrowing to what's already been proven true, not introducing a new
        // assumption. Needed because MindDuelBrain's methods (and MindDuelPulseContext's actor field)
        // are typed as LivingEntity, matching what every other mind-duel resolver in this codebase
        // already expects a duel participant to be.
        LivingEntity livingEntity = (LivingEntity) entity;

        // Addon extension point (see com.dragonspeech.api.MindDuelBrain's own doc) - when a brain is
        // registered AND accepts this entity, its decision is used INSTEAD of everything below, every
        // pulse it chooses to act. PASS (or no brain registered at all) falls through to the built-in
        // heuristic exactly as if nothing had changed.
        var brain = com.dragonspeech.api.MindDuelBrainRegistry.get();
        if (brain != null && brain.handles(livingEntity, tier)) {
            var context = new com.dragonspeech.api.MindDuelPulseContext(
                    livingEntity, tier, actorIsAttacker, actorIsAttacker ? duel.defenderId() : duel.attackerId(),
                    duel.phase(),
                    actorIsAttacker ? duel.defenderBarrierIntegrity() : duel.attackerBarrierIntegrity(),
                    actorIsAttacker ? duel.attackerBarrierIntegrity() : duel.defenderBarrierIntegrity());
            var decision = brain.decide(context);
            if (!decision.isPass()) {
                if (decision.crackClickAttempts() > 0) {
                    for (int i = 0; i < decision.crackClickAttempts(); i++) {
                        attemptCrackClick(server, duel, actorId, actorIsAttacker);
                    }
                } else if (decision.action() != null) {
                    MindDuelActionService.resolve(server, duel, actorId, decision.action(), decision.actionParam());
                }
                return;
            }
            // decision.isPass() - fall through to the built-in heuristic below for just this pulse.
        }

        if (duel.phase() == DuelPhase.DEFENSE_BREACH) {
            int attempts = clickAttemptsPerPulse(tier, entity.getType());
            for (int i = 0; i < attempts; i++) {
                attemptCrackClick(server, duel, actorId, actorIsAttacker);
            }
            return;
        }

        // After the barrier breaks, mobs only need to issue connected-mind commands.
        if (RANDOM.nextFloat() > 0.55f) {
            return;
        }
        DuelAction action = chooseAction1v1(duel, actorIsAttacker);
        if (action == null) {
            return;
        }
        String param = paramFor1v1(duel, actorIsAttacker, action);
        MindDuelActionService.resolve(server, duel, actorId, action, param);
    }

    /**
     * Higher-tier minds react faster - this is the actual "own speed depending on tier" the design
     * calls for during Defense Breach. A per-entity-type override (see MindFortitudeService.
     * REACTION_OVERRIDES, editable from the config GUI's Sentience Editor) takes priority when
     * present, letting an operator fine-tune reaction speed for a specific mob beyond what its
     * SentienceTier alone implies - unbounded rather than locked to these 3 preset values.
     */
    private static int clickAttemptsPerPulse(SentienceTier tier, net.minecraft.world.entity.EntityType<?> type) {
        Integer override = MindFortitudeService.REACTION_OVERRIDES.get(type);
        if (override != null) {
            return Math.max(0, override);
        }
        return switch (tier) {
            case DRAGON -> 4;
            case CHAOTIC, DISCIPLINED -> 2;
            default -> 1; // TRAINED - the minimum tier capable of acting at all
        };
    }

    private static void attemptCrackClick(MinecraftServer server, ActiveMindDuel duel, UUID actorId, boolean actorIsAttacker) {
        long now = server.overworld().getGameTime();

        // A mob now has to split its attention between striking the
        // opponent's barrier and sealing its own, same as a player does -
        // this simple heuristic mostly favors striking, but shifts
        // toward sealing once its own barrier is in real danger, giving
        // it at least a basic sense of "defend when threatened."
        float ownIntegrity = actorIsAttacker ? duel.attackerBarrierIntegrity() : duel.defenderBarrierIntegrity();
        float sealChance = ownIntegrity < 40f ? 0.7f : 0.4f;
        boolean wantsToSeal = RANDOM.nextFloat() < sealChance;

        boolean targetsAttackerBarrier = actorIsAttacker == wantsToSeal;
        BreachState breach = targetsAttackerBarrier ? duel.attackerBreach() : duel.defenderBreach();

        if (wantsToSeal && breach.cracks().isEmpty()) {
            return; // nothing to mend right now - just skip this attempt rather than forcing a strike instead
        }

        long cooldownUntil = wantsToSeal ? breach.defenderNextClickTime() : breach.attackerNextClickTime();
        if (now < cooldownUntil) {
            return;
        }

        BarType card = chooseMobCard();
        DuelAction action = wantsToSeal ? DuelAction.SEAL_CRACK : DuelAction.STRIKE_CRACK;
        String param;
        if (wantsToSeal) {
            Crack target = breach.cracks().get(RANDOM.nextInt(breach.cracks().size()));
            param = card.getSerializedName() + ":" + target.id();
        } else {
            param = card.getSerializedName() + ":" + (0.15f + RANDOM.nextFloat() * 0.7f) + ":" + (0.15f + RANDOM.nextFloat() * 0.7f);
        }
        MindDuelActionService.resolve(server, duel, actorId, action, param);
    }

    private static BarType chooseMobCard() {
        List<BarType> pool = new java.util.ArrayList<>();
        addWeighted(pool, BarType.STAMINA, 4);
        addWeighted(pool, BarType.SPEED, 3);
        addWeighted(pool, BarType.WILLPOWER, 2);
        addWeighted(pool, BarType.POWER, 2);
        addWeighted(pool, BarType.FOCUS, Math.round(MOB_FOCUS_CARD_WEIGHT));
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static void addWeighted(List<BarType> pool, BarType type, int weight) {
        for (int i = 0; i < weight; i++) {
            pool.add(type);
        }
    }

    private static void maybeActTeamAttacker(MinecraftServer server, TeamMindDuel duel, UUID attackerId) {
        if (mobTierIfCapable(EntityLookup.byUUID(server, attackerId)) == null) {
            return;
        }
        if (RANDOM.nextFloat() > 0.55f) {
            return;
        }
        List<UUID> candidates = duel.defenders().keySet().stream()
                .filter(id -> !duel.isDowned(id))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        TeamDuelAction action = chooseTeamAttackerAction();
        UUID target = action == TeamDuelAction.DISRUPT_LINKS ? null : candidates.get(RANDOM.nextInt(candidates.size()));
        TeamMindDuelService.resolve(server, duel, attackerId, action, target);
    }

    /** Returns the mob's SentienceTier if it's a non-player capable of acting, or null otherwise - null doubles as both "not a mob" and "not capable," since callers only ever branch on whether they got a usable tier back. */
    private static SentienceTier mobTierIfCapable(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity instanceof ServerPlayer) {
            return null;
        }
        SentienceTier tier = MindFortitudeService.classifyTier(living);
        // Every mob capable of being duelled at all fights back during
        // Defense Breach - a Zombie or Skeleton mends/strikes slowly and
        // clumsily (see clickAttemptsPerPulse), a Warden or Enderman does
        // it fast. SentienceTier controls HOW WELL, never WHETHER - a mob
        // that can be reached at all is expected to defend itself, per
        // the original design ("they need to follow the same rules as
        // the player. They have the 5 bars..."). canActInDuel() (a much
        // stricter TRAINED-or-above gate) still applies separately to
        // whether a mob is clever enough to issue post-access commands
        // as an attacker - see chooseAction1v1's OCCUPIED_MIND branch.
        return tier.canBeDueled() ? tier : null;
    }

    // ---------------------------------------------------------------- decision heuristics (commands)

    private static DuelAction chooseAction1v1(ActiveMindDuel duel, boolean isAttacker) {
        return switch (duel.phase()) {
            case DEFENSE_BREACH -> null; // handled entirely by attemptCrackClick() above
            case OCCUPIED_MIND, TRUE_NAME_DOMINATION -> isAttacker ? DuelAction.ISSUE_COMMAND : null;
            case CONTACT, ENDED -> null;
        };
    }

    private static TeamDuelAction chooseTeamAttackerAction() {
        return weightedTeam(TeamDuelAction.OVERWHELM, 3, TeamDuelAction.WEAR_THEM_DOWN, 2,
                TeamDuelAction.ISOLATE, 2, TeamDuelAction.FALSE_TARGETS, 1, TeamDuelAction.DISRUPT_LINKS, 1);
    }

    private static String paramFor1v1(ActiveMindDuel duel, boolean isAttacker, DuelAction action) {
        if (action == DuelAction.ISSUE_COMMAND) {
            List<String> commandIds = List.copyOf(CommandEffectRegistry.all().keySet());
            return commandIds.isEmpty() ? "" : commandIds.get(RANDOM.nextInt(commandIds.size()));
        }
        return "";
    }

    private static TeamDuelAction weightedTeam(Object... actionsAndWeights) {
        List<TeamDuelAction> pool = new java.util.ArrayList<>();
        for (int i = 0; i < actionsAndWeights.length; i += 2) {
            TeamDuelAction action = (TeamDuelAction) actionsAndWeights[i];
            int weight = (Integer) actionsAndWeights[i + 1];
            for (int w = 0; w < weight; w++) {
                pool.add(action);
            }
        }
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    // ---------------------------------------------------------------- initiating new duels

    private static void initiateTick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (MindDuelManager.isInDuel(player.getUUID()) || TeamMindDuelManager.isInDuel(player.getUUID())) {
                continue;
            }
            AABB range = player.getBoundingBox().inflate(INITIATE_RANGE);
            List<Mob> nearby = player.level().getEntitiesOfClass(Mob.class, range,
                    mob -> mob instanceof Monster && !MindDuelManager.isInDuel(mob.getUUID()) && !TeamMindDuelManager.isInDuel(mob.getUUID())
                            && MindFortitudeService.classifyTier(mob).canActInDuel());

            for (Mob mob : nearby) {
                // Addon extension point (see com.dragonspeech.api.MindDuelBrain#shouldInitiateDuel) -
                // checked before the built-in flat-chance roll, only for mobs the registered brain
                // (if any) actually accepted via handles().
                var brain = com.dragonspeech.api.MindDuelBrainRegistry.get();
                if (brain != null) {
                    SentienceTier tier = MindFortitudeService.classifyTier(mob);
                    if (brain.handles(mob, tier)) {
                        Boolean decision = brain.shouldInitiateDuel(mob, tier, player);
                        if (decision != null) {
                            if (decision) {
                                ContactResolver.attemptAsMob(mob, player);
                                break;
                            }
                            continue; // brain explicitly said "not now" - skip the random roll for this mob
                        }
                        // decision == null - fall through to the built-in flat-chance roll below.
                    }
                }
                if (RANDOM.nextFloat() < INITIATE_CHANCE_PER_CANDIDATE) {
                    ContactResolver.attemptAsMob(mob, player);
                    break; // at most one new duel started per player per pulse
                }
            }
        }
    }
}