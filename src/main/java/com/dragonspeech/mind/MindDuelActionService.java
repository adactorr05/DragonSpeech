package com.dragonspeech.mind;

import com.dragonspeech.storage.SkillsAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;
import java.util.UUID;

/**
 * The single entry point every duel-action network call goes through,
 * for every phase past Contact (Contact itself is ContactResolver, since
 * no ActiveMindDuel exists yet at that point). One method, resolve(),
 * dispatches on duel.phase().
 *
 * Defense Breach redesign: the mechanic is now direct clicking. The
 * attacker picks a BarType card and clicks a crack (STRIKE_CRACK); the
 * defender picks a BarType card and clicks the SAME crack to mend it
 * (SEAL_CRACK). Each bar has its own impact/cost profile (see BarType) -
 * Focus is the highest-impact, highest-cost, and the only one that ends
 * the whole encounter the instant it hits 0 on either side. A per-side
 * click cooldown (shortened by Speed) is what keeps this a reaction
 * game rather than a macro contest.
 */
public final class MindDuelActionService {

    private static final Random RANDOM = new Random();

    // --- Defense Breach click economy
    /** Base cooldown between clicks, before Speed adjusts it. */
    private static final long BASE_CLICK_COOLDOWN_MILLIS = 350L;
    private static final long MIN_CLICK_COOLDOWN_MILLIS = 120L;
    /** Base cost taken from whichever bar was selected, before that bar's own cost multiplier. */
    private static final float BASE_CLICK_COST = 6f;
    /** Base crack openness change per click, before power multiplier and the selected bar's impact multiplier. */
    private static final float BASE_CLICK_IMPACT = 14f;

    private MindDuelActionService() {}

    public static DuelActionResult resolve(MinecraftServer server, ActiveMindDuel duel, UUID actorId, DuelAction action, String param) {
        boolean actorIsAttacker = duel.isAttacker(actorId);
        long now = server.overworld().getGameTime();
        duel.touch(now);

        if (duel.pendingRoleChoice()) {
            if (actorIsAttacker) {
                return DuelActionResult.illegal("Their focus has broken - wait to see what they choose to do.");
            }
            if (action == DuelAction.SEIZE_CONTROL) {
                return resolveSeizeControl(server, duel, actorId, now);
            }
            if (action != DuelAction.DISENGAGE) {
                return DuelActionResult.illegal("You must choose: flee (Disengage), or seize control.");
            }
        }

        if (action == DuelAction.DISENGAGE) {
            DuelOutcome outcome = actorIsAttacker ? DuelOutcome.DEFENDER_VICTORY : DuelOutcome.ATTACKER_VICTORY;
            MindDuelService.end(server, duel, outcome, actorIsAttacker
                ? "The attacker breaks contact and withdraws."
                : "You flee rather than risk seizing control - the attacker is left standing at the threshold of your mind.");
            MindDuelSyncHooks.pushSync(server, duel);
            return DuelActionResult.ended("You withdraw from the duel.", outcome);
        }

        DuelActionResult result = switch (duel.phase()) {
            case DEFENSE_BREACH -> resolveBreach(server, duel, actorId, actorIsAttacker, action, param, now);
            case OCCUPIED_MIND, TRUE_NAME_DOMINATION -> resolveCommandPhase(server, duel, actorIsAttacker, action, param);
            case CONTACT, ENDED -> DuelActionResult.illegal("There is no active duel action to take right now.");
        };
        MindDuelSyncHooks.pushSync(server, duel);
        return result;
    }

    // ---------------------------------------------------------------- Defense Breach (click-based)

    private static DuelActionResult resolveBreach(MinecraftServer server, ActiveMindDuel duel, UUID actorId, boolean actorIsAttacker,
                                                    DuelAction action, String param, long now) {
        if (action != DuelAction.STRIKE_CRACK && action != DuelAction.SEAL_CRACK) {
            return DuelActionResult.illegal("Choose a card, then click a barrier.");
        }

        // Both roles can do both actions now - STRIKE always targets the
        // OPPONENT's barrier, SEAL always targets YOUR OWN. Which of the
        // two BreachState objects (and which integrity counter) a given
        // click actually affects follows entirely from (actorIsAttacker,
        // action) - there's no separate "which barrier" identifier
        // needed in the click param at all.
        boolean targetsAttackerBarrier = actorIsAttacker == (action == DuelAction.SEAL_CRACK);
        BreachState breach = targetsAttackerBarrier ? duel.attackerBreach() : duel.defenderBreach();

        String[] parts = (param == null ? "" : param).split(":");
        BarType bar;
        try {
            bar = BarType.valueOf(parts[0].trim().toUpperCase());
        } catch (RuntimeException e) {
            return DuelActionResult.illegal("That is not a recognized card.");
        }

        MindCombatant actor = actorIsAttacker ? duel.attacker() : duel.defender();

        // BreachState's own attackerNextClickTime()/defenderNextClickTime()
        // mean "whoever STRIKES this specific barrier's cooldown" and
        // "whoever SEALS this specific barrier's cooldown" respectively -
        // NOT "the duel's attacker" / "the duel's defender". Since every
        // barrier is struck by one role and sealed by the other
        // consistently, this distinction is exactly action-based, same as
        // the barrier selection above.
        boolean isStrike = action == DuelAction.STRIKE_CRACK;
        long cooldownUntil = isStrike ? breach.attackerNextClickTime() : breach.defenderNextClickTime();
        if (now < cooldownUntil) {
            return DuelActionResult.illegal("Too fast - your mind needs a moment to steady before the next click.");
        }

        float clickX = 0f;
        float clickY = 0f;
        int crackId = -1;
        if (isStrike) {
            if (parts.length != 3) {
                return DuelActionResult.illegal("Click somewhere on the barrier to crack it.");
            }
            try {
                clickX = Float.parseFloat(parts[1].trim());
                clickY = Float.parseFloat(parts[2].trim());
            } catch (NumberFormatException e) {
                return DuelActionResult.illegal("That barrier click was not understood.");
            }
        } else {
            if (parts.length != 2) {
                return DuelActionResult.illegal("Click a visible crack to mend it.");
            }
            try {
                crackId = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return DuelActionResult.illegal("That crack was not recognized.");
            }
            if (breach.findById(crackId) == null) {
                return DuelActionResult.illegal("That crack is no longer open.");
            }
        }

        float cost = BASE_CLICK_COST * bar.costMultiplier();
        if (!actor.canAfford(bar, cost)) {
            return DuelActionResult.illegal("Your " + bar.getSerializedName() + " is too low to spend on that.");
        }
        actor.spend(bar, cost);

        long cooldownMillis = Math.max(MIN_CLICK_COOLDOWN_MILLIS, BASE_CLICK_COOLDOWN_MILLIS - actor.speedCooldownAdjustMillis());
        long cooldownTicks = Math.max(1L, cooldownMillis / 50L); // 50ms per game tick
        long nextClickTime = now + cooldownTicks;
        if (isStrike) {
            breach.setAttackerNextClickTime(nextClickTime);
        } else {
            breach.setDefenderNextClickTime(nextClickTime);
        }

        float impact = BASE_CLICK_IMPACT * bar.impactMultiplier() * actor.powerMultiplier();

        if (isStrike) {
            float burstDamage = breach.strikeAt(clickX, clickY, impact);
            if (burstDamage > 0f) {
                if (targetsAttackerBarrier) {
                    duel.damageAttackerBarrier(burstDamage);
                } else {
                    duel.damageDefenderBarrier(burstDamage);
                }
            }
        } else {
            breach.seal(crackId, impact);
        }

        // Focus is the special bar: hitting 0 collapses that side's OWN
        // barrier outright, for either side.
        if (bar == BarType.FOCUS || actor.isFocusBroken()) {
            DuelActionResult focusResult = checkFocusBroken(server, duel, now);
            if (focusResult != null) {
                return focusResult;
            }
        }

        return afterBarrierChange(server, duel, isStrike
            ? "You strike the barrier."
            : "You work to mend the crack.");
    }

    /**
     * The Focus-specific end condition. Either side's Focus hitting 0
     * now just collapses THEIR OWN barrier outright (rather than the old
     * one-barrier design's asymmetric "attacker is forced out / defender
     * gets a choice" split) - it flows through the exact same unified
     * win-check every other barrier depletion does. Returns null if
     * neither side's Focus actually broke (the normal case - most clicks
     * don't drain Focus at all, since only the FOCUS card touches it,
     * plus physical interruption - see MindDuelService.onPhysicalDamage).
     */
    private static DuelActionResult checkFocusBroken(MinecraftServer server, ActiveMindDuel duel, long now) {
        if (duel.attacker().isFocusBroken()) {
            duel.damageAttackerBarrier(999f);
            return afterBarrierChange(server, duel, "Your focus gives out entirely, and your own barrier collapses with it.");
        }
        if (duel.defender().isFocusBroken()) {
            duel.damageDefenderBarrier(999f);
            return afterBarrierChange(server, duel, "Their focus gives out entirely, and their own barrier collapses with it.");
        }
        return null;
    }

    private static DuelActionResult resolveSeizeControl(MinecraftServer server, ActiveMindDuel duel, UUID actorId, long now) {
        duel.swapRoles(now);
        var newDefenderEntity = EntityLookup.byUUID(server, duel.defenderId());
        if (newDefenderEntity instanceof net.minecraft.world.entity.LivingEntity newDefenderLiving) {
            duel.setDefenderTier(MindFortitudeService.classifyTier(newDefenderLiving));
        }
        MindDuelSyncHooks.pushSync(server, duel);
        return DuelActionResult.ok("You seize control! The roles reverse - you are the attacker now, and their mind is the one under siege.");
    }

    /**
     * Unified win-check for both barriers - whichever breaks first
     * decides the outcome. If the DEFENDER's barrier breaks, this plays
     * out exactly as it always has: the attacker connects to the
     * defender's mind. If the ATTACKER's barrier breaks FIRST, roles
     * swap (swapRolesForVictory()) and the former defender - the actual
     * winner - is the one who connects, into what's now the loser's
     * mind. This is what actually gives the defender a real way to win,
     * rather than only ever being able to stall.
     */
    private static DuelActionResult afterBarrierChange(MinecraftServer server, ActiveMindDuel duel, String message) {
        // FIX: real root cause of "Dragon Heart duel still opens Occupied
        // Mind instead of the Dragon Heart screen" - a Dragon Heart
        // vessel (see DragonHeartVesselEntity's own doc) has no commands
        // to issue and shouldn't be able to "command" the winning
        // player's mind either. Neither branch below should transition
        // it into OCCUPIED_MIND at all - the duel should simply conclude,
        // which is what actually fires MindDuelService.END_LISTENERS
        // (what DragonHeartService.onDuelEnded, and therefore the entire
        // Dragon Heart screen, depends on). This was the missing piece
        // this whole time - onDuelEnded was correctly written, it just
        // never actually got called for a normal breach victory, only
        // from a later, separate ending action like Disengage.
        boolean defenderIsHeartVessel = EntityLookup.byUUID(server, duel.defenderId())
            instanceof com.dragonspeech.eldunari.DragonHeartVesselEntity;

        if (duel.defenderBarrierIntegrity() <= 0f) {
            if (defenderIsHeartVessel) {
                MindDuelService.end(server, duel, DuelOutcome.ATTACKER_VICTORY, message + " Their barrier shatters completely.");
                return DuelActionResult.ok(message + " Their barrier shatters completely.");
            }
            duel.setPhase(DuelPhase.OCCUPIED_MIND);
            TrueNameDuelHooks.unlockProgress(server, duel.attackerId(), duel.defenderId());
            return DuelActionResult.ok(message + " Their barrier shatters completely. You are connected to their mind.");
        }
        if (duel.attackerBarrierIntegrity() <= 0f) {
            if (defenderIsHeartVessel) {
                // The vessel "won" - conceptually this just means the
                // duel is lost, not that an inert heart now commands the
                // player's mind. Concludes the same way, from the
                // player's own losing side.
                MindDuelService.end(server, duel, DuelOutcome.DEFENDER_VICTORY, message + " Your own barrier shatters completely - the heart's resistance was too strong.");
                return DuelActionResult.ok(message + " Your own barrier shatters completely - the heart's resistance was too strong.");
            }
            long now = server.overworld().getGameTime();
            duel.swapRolesForVictory(now);
            var newDefenderEntity = EntityLookup.byUUID(server, duel.defenderId());
            if (newDefenderEntity instanceof net.minecraft.world.entity.LivingEntity newDefenderLiving) {
                duel.setDefenderTier(MindFortitudeService.classifyTier(newDefenderLiving));
            }
            duel.setPhase(DuelPhase.OCCUPIED_MIND);
            TrueNameDuelHooks.unlockProgress(server, duel.attackerId(), duel.defenderId());
            return DuelActionResult.ok(message + " Their barrier shatters completely - you have broken through first. You are connected to their mind.");
        }
        return DuelActionResult.ok(message);
    }

    // ---------------------------------------------------------------- Occupied Mind

    /**
     * REMOVED: the SPEAK_TRUE_NAME branch that used to live here (a
     * risky "guess it right now or the duel ends" mid-duel action) has
     * been replaced entirely by TrueNameProgressService - winning a
     * duel now unlocks a permanent Grimoire entry instead, and the
     * actual guessing happens later, outside combat, gradually. See
     * TrueNameDuelHooks for the win-time unlock. resolveSpeakTrueName()
     * itself is left in the file below, UNREACHABLE from here rather
     * than deleted - full removal would mean tracing every reference to
     * DuelPhase.TRUE_NAME_DOMINATION/duel.trueNameKnown() elsewhere in
     * the mind-duel system first, which is real, separate work; making
     * the entry point unreachable achieves "this can never trigger
     * again" without that risk.
     */
    private static DuelActionResult resolveCommandPhase(MinecraftServer server, ActiveMindDuel duel, boolean actorIsAttacker, DuelAction action, String param) {
        if (action == DuelAction.RESIST && !actorIsAttacker) {
            return MindControlService.resist(server, duel);
        }
        if (action != DuelAction.ISSUE_COMMAND || !actorIsAttacker) {
            return DuelActionResult.illegal("Only the attacker may issue commands, and only the defender resists them - that happens automatically.");
        }

        CommandEffect effect = CommandEffectRegistry.get(param == null ? "" : param).orElse(null);
        if (effect != null) {
            var defenderRaw = EntityLookup.byUUID(server, duel.defenderId());
            var attackerRaw = EntityLookup.byUUID(server, duel.attackerId());
            if (defenderRaw instanceof net.minecraft.world.entity.LivingEntity livingDefender
                && attackerRaw instanceof net.minecraft.world.entity.LivingEntity livingAttacker) {
                effect.apply().run(server, livingDefender, livingAttacker);
            }
            return DuelActionResult.ok("The command takes hold: \"" + effect.label() + "\".");
        }
        return DuelActionResult.ok("The command takes hold, vague as it was.");
    }

    @SuppressWarnings("unused") // intentionally unreachable now - see resolveCommandPhase's own doc
    private static DuelActionResult resolveSpeakTrueName(MinecraftServer server, ActiveMindDuel duel, boolean actorIsAttacker, String guess) {
        if (!actorIsAttacker) {
            return DuelActionResult.illegal("Only an attacker can speak a name into a connected mind.");
        }
        ServerPlayer attacker = server.getPlayerList().getPlayer(duel.attackerId());
        var defenderRaw = EntityLookup.byUUID(server, duel.defenderId());
        if (attacker == null || !(defenderRaw instanceof net.minecraft.world.entity.LivingEntity defender)) {
            return DuelActionResult.illegal("There is no mind here with a true name to speak.");
        }
        if (!SkillsAccess.get(attacker).canBindTotally()) {
            return DuelActionResult.illegal("You have not learned to bind mind to mind with the precision a true name demands.");
        }
        if (guess == null || guess.isBlank()) {
            return DuelActionResult.illegal("You must actually speak a name.");
        }

        if (!TrueNameService.guessCorrectForEntity(defender, guess)) {
            // Per the design: a wrong guess doesn't just fail quietly -
            // it costs the whole connection. Speaking a false name into
            // someone's mind is a real, detectable violation of it.
            MindDuelService.end(server, duel, DuelOutcome.DEFENDER_VICTORY,
                "You speak a name, but it is wrong - and the mind you're touching feels the lie. The connection shatters.");
            return DuelActionResult.ended("Wrong name - the connection is severed.", DuelOutcome.DEFENDER_VICTORY);
        }

        duel.setTrueNameKnown(true);
        duel.setPhase(DuelPhase.TRUE_NAME_DOMINATION);
        return DuelActionResult.ok("You speak their true name, and it is correct. Their mind has no more secrets, and little left to resist with.");
    }

}
