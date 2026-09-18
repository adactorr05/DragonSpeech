package com.dragonspeech.mind;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;
import java.util.UUID;

/**
 * The team-duel analog of MindDuelActionService. Reuses DuelActionResult
 * and DuelOutcome from the 1v1 system directly - "the attacker was
 * forced out" or "the team was defeated" mean the same thing regardless
 * of how many minds were on the defending side, so there's no reason for
 * a second result/outcome type to exist.
 */
public final class TeamMindDuelService {

    private static final Random RANDOM = new Random();

    private static final float ISOLATE_COST = 25f;
    private static final float OVERWHELM_COST = 25f;
    private static final float DISRUPT_LINKS_COST = 20f;
    private static final float FALSE_TARGETS_COST = 12f;
    private static final float WEAR_THEM_DOWN_COST = 10f;

    private static final float REINFORCE_COST = 15f;
    private static final float PROTECT_COST = 10f;
    private static final float REVIVE_COST = 30f;
    private static final float FOCUS_BURST_COST_PER_MEMBER = 15f;
    private static final float TEAM_RESTORE_FOCUS_COST = 10f;

    private TeamMindDuelService() {}

    public static DuelActionResult resolve(MinecraftServer server, TeamMindDuel duel, UUID actorId, TeamDuelAction action, UUID targetMemberId) {
        duel.touch(server.overworld().getGameTime());

        if (action == TeamDuelAction.DISENGAGE) {
            boolean actorIsAttacker = duel.isAttacker(actorId);
            DuelOutcome outcome = actorIsAttacker ? DuelOutcome.DEFENDER_VICTORY : DuelOutcome.ATTACKER_VICTORY;
            TeamMindDuelService.end(server, duel, outcome, "One side breaks contact, and the battle ends.");
            return DuelActionResult.ended("You withdraw from the duel.", outcome);
        }

        DuelActionResult result = duel.isAttacker(actorId)
            ? resolveAttacker(server, duel, action, targetMemberId)
            : resolveDefender(server, duel, actorId, action, targetMemberId);

        TeamMindDuelSyncHooks.pushSync(server, duel);
        return result;
    }

    private static DuelActionResult resolveAttacker(MinecraftServer server, TeamMindDuel duel, TeamDuelAction action, UUID targetMemberId) {
        MindCombatant attacker = duel.attacker();

        if (action == TeamDuelAction.DISRUPT_LINKS) {
            return spend(attacker, DISRUPT_LINKS_COST, () -> {
                duel.damageLinkStrength(15f + RANDOM.nextInt(8));
                return checkTeamDefeated(server, duel, "You tear at the bond holding them together.");
            });
        }

        MindCombatant target = targetMemberId != null ? duel.defender(targetMemberId) : null;
        if (target == null) {
            return DuelActionResult.illegal("You must choose which of them to target.");
        }
        if (duel.isDowned(targetMemberId)) {
            return DuelActionResult.illegal("That mind has already gone dark - there's nothing left there to strike.");
        }

        return switch (action) {
            case ISOLATE -> spend(attacker, ISOLATE_COST, () -> {
                MindLinkManager.get(targetMemberId).ifPresent(link -> {
                    if (RANDOM.nextFloat() * 100f < (100f - duel.linkStrength())) {
                        link.isolate(targetMemberId);
                    }
                });
                duel.damageLinkStrength(8f);
                boolean nowIsolated = MindLinkManager.get(targetMemberId).map(l -> l.isIsolated(targetMemberId)).orElse(false);
                return checkTeamDefeated(server, duel, nowIsolated
                    ? "You cut one mind off from the rest. They stand alone now."
                    : "You try to isolate them, but the bond holds - for now.");
            });
            case OVERWHELM -> spend(attacker, OVERWHELM_COST, () -> {
                target.damageFocus(20f + RANDOM.nextInt(8));
                return checkTeamDefeated(server, duel, "You bring everything to bear on a single mind.");
            });
            case FALSE_TARGETS -> spend(attacker, FALSE_TARGETS_COST, () -> {
                target.damageFocus(6f);
                duel.damageLinkStrength(4f);
                return checkTeamDefeated(server, duel, "You throw up false targets, sowing confusion through the link.");
            });
            case WEAR_THEM_DOWN -> spend(attacker, WEAR_THEM_DOWN_COST, () -> {
                target.drainStamina(10f + RANDOM.nextInt(5));
                return checkTeamDefeated(server, duel, "You grind at their stamina, patient and cheap.");
            });
            default -> DuelActionResult.illegal("That is not an attacker action.");
        };
    }

    private static DuelActionResult resolveDefender(MinecraftServer server, TeamMindDuel duel, UUID actorId, TeamDuelAction action, UUID targetMemberId) {
        MindCombatant actor = duel.defender(actorId);
        if (actor == null) {
            return DuelActionResult.illegal("You are not part of this link.");
        }
        if (duel.isDowned(actorId) && action != TeamDuelAction.RESTORE_FOCUS) {
            return DuelActionResult.illegal("Your own mind has gone dark - you can only wait for a teammate to revive you.");
        }

        return switch (action) {
            case RESTORE_FOCUS -> spend(actor, TEAM_RESTORE_FOCUS_COST, () -> {
                actor.restoreFocus(15f);
                return DuelActionResult.ok("You pull your own mind back together.");
            });
            case REINFORCE -> {
                MindCombatant target = targetMemberId != null ? duel.defender(targetMemberId) : null;
                if (target == null) {
                    yield DuelActionResult.illegal("You must choose an ally to reinforce.");
                }
                yield spend(actor, REINFORCE_COST, () -> {
                    float transferred = Math.min(12f, actor.focus());
                    actor.damageFocus(transferred);
                    target.restoreFocus(transferred);
                    return DuelActionResult.ok("You send part of your own focus to hold them steady.");
                });
            }
            case PROTECT -> {
                MindCombatant target = targetMemberId != null ? duel.defender(targetMemberId) : null;
                if (target == null) {
                    yield DuelActionResult.illegal("You must choose an ally to protect.");
                }
                yield spend(actor, PROTECT_COST, () -> {
                    // No persistent "shield" flag system yet - approximated
                    // as an immediate partial heal, standing in for
                    // "absorbed some of what was coming".
                    target.restoreFocus(8f);
                    return DuelActionResult.ok("You step between them and whatever comes next.");
                });
            }
            case REVIVE -> {
                MindCombatant target = targetMemberId != null ? duel.defender(targetMemberId) : null;
                if (target == null || !duel.isDowned(targetMemberId)) {
                    yield DuelActionResult.illegal("There is no downed ally there to revive.");
                }
                yield spend(actor, REVIVE_COST, () -> {
                    target.restoreFocus(Math.max(15f, target.maxFocus() * 0.25f));
                    target.restoreStamina(Math.max(15f, target.maxStamina() * 0.25f));
                    return DuelActionResult.ok("You drag them back from the dark, at real cost to yourself.");
                });
            }
            case FOCUS_BURST -> resolveFocusBurst(server, duel, actorId);
            default -> DuelActionResult.illegal("That is not a defender action.");
        };
    }

    private static DuelActionResult resolveFocusBurst(MinecraftServer server, TeamMindDuel duel, UUID actorId) {
        MindLink link = MindLinkManager.get(actorId).orElse(null);
        if (link == null) {
            return DuelActionResult.illegal("There is no link to draw a combined strike from.");
        }
        int contributors = 0;
        for (UUID memberId : link.connectedMembers()) {
            MindCombatant member = duel.defender(memberId);
            if (member != null && !member.isBroken() && member.canAffordStamina(FOCUS_BURST_COST_PER_MEMBER)) {
                member.drainStamina(FOCUS_BURST_COST_PER_MEMBER);
                contributors++;
            }
        }
        if (contributors == 0) {
            return DuelActionResult.illegal("No one has the stamina left to contribute to a combined strike.");
        }
        float damage = contributors * 12f;
        duel.attacker().damageFocus(damage);
        DuelActionResult end = checkAttackerBroken(server, duel, "The whole link strikes as one enormous blow.");
        return end != null ? end : DuelActionResult.ok(contributors + " linked minds strike as one, tearing at the attacker's focus.");
    }

    // ---------------------------------------------------------------- shared

    private static DuelActionResult checkTeamDefeated(MinecraftServer server, TeamMindDuel duel, String message) {
        if (duel.isTeamDefeated()) {
            end(server, duel, DuelOutcome.ATTACKER_VICTORY, message + " The last of them falls silent. The link is broken.");
            return DuelActionResult.ended(message + " The team is defeated.", DuelOutcome.ATTACKER_VICTORY);
        }
        return DuelActionResult.ok(message);
    }

    private static DuelActionResult checkAttackerBroken(MinecraftServer server, TeamMindDuel duel, String message) {
        if (duel.attacker().isBroken()) {
            end(server, duel, DuelOutcome.DEFENDER_VICTORY, message + " The attacker's will gives out, and they are cast from the link entirely.");
            return DuelActionResult.ended(message + " The attacker is forced out.", DuelOutcome.DEFENDER_VICTORY);
        }
        return null;
    }

    public static void end(MinecraftServer server, TeamMindDuel duel, DuelOutcome outcome, String message) {
        if (duel.ended()) {
            return;
        }
        duel.markEnded();
        TeamMindDuelManager.end(duel.duelId());

        ServerPlayer attacker = server.getPlayerList().getPlayer(duel.attackerId());
        if (attacker != null) {
            attacker.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
            MindDuelService.applyMentalFatigue(attacker, outcome == DuelOutcome.ATTACKER_VICTORY);
        }
        for (UUID memberId : duel.defenders().keySet()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                member.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
                MindDuelService.applyMentalFatigue(member, outcome == DuelOutcome.DEFENDER_VICTORY);
            }
        }
        TeamMindDuelSyncHooks.pushSync(server, duel);
    }

    private interface Effect {
        DuelActionResult apply();
    }

    private static DuelActionResult spend(MindCombatant actor, float staminaCost, Effect effect) {
        if (!actor.canAffordStamina(staminaCost)) {
            return DuelActionResult.illegal("You are too exhausted to do that right now.");
        }
        actor.drainStamina(staminaCost);
        return effect.apply();
    }
}
