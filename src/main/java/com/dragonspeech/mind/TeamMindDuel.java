package com.dragonspeech.mind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The N-vs-1 analog of ActiveMindDuel (design notes idea 7 - "Three
 * elves defending vs. One Shade attacking"). Deliberately a SEPARATE
 * class rather than a generalized ActiveMindDuel: the 1v1 duel's five-
 * phase structure (Breach -> Hidden Core -> Struggle -> Occupied Mind ->
 * True Name) doesn't obviously generalize to a team fight, and forcing
 * it to would risk breaking the working 1v1 system for an uncertain
 * payoff. This class instead models the single, most fully-specified
 * multi-person scenario from the design notes: one shared battle phase
 * where the team's Focus/Stamina and the attacker's are directly
 * contested, same axis as 1v1 Struggle, plus the team-only Link
 * Strength meter and coordination actions (Reinforce/Protect/Revive/
 * Focus Burst). See docs/MIND_DUEL_PHASE6.md for what a full Breach/
 * Hidden Core/Occupied Mind/True Name pass for teams would still need.
 */
public final class TeamMindDuel {

    private final UUID duelId;
    private final UUID attackerId;
    private final MindCombatant attacker;
    private final UUID linkId;
    /** Insertion order preserved so status displays list members consistently. */
    private final Map<UUID, MindCombatant> defenders = new LinkedHashMap<>();

    private float linkStrength = 100f;
    private boolean ended = false;
    private long lastActionGameTime;
    private int interruptionsThisPhase = 0;

    public TeamMindDuel(UUID attackerId, MindCombatant attacker, UUID linkId, Map<UUID, MindCombatant> defenders) {
        this.duelId = UUID.randomUUID();
        this.attackerId = attackerId;
        this.attacker = attacker;
        this.linkId = linkId;
        this.defenders.putAll(defenders);
    }

    public UUID duelId() {
        return duelId;
    }

    public UUID attackerId() {
        return attackerId;
    }

    public MindCombatant attacker() {
        return attacker;
    }

    public UUID linkId() {
        return linkId;
    }

    public Map<UUID, MindCombatant> defenders() {
        return defenders;
    }

    public MindCombatant defender(UUID playerId) {
        return defenders.get(playerId);
    }

    public boolean isAttacker(UUID entityId) {
        return attackerId.equals(entityId);
    }

    public boolean isDefender(UUID entityId) {
        return defenders.containsKey(entityId);
    }

    public float linkStrength() {
        return linkStrength;
    }

    public void damageLinkStrength(float amount) {
        linkStrength = Math.max(0f, linkStrength - Math.max(0f, amount));
    }

    public void repairLinkStrength(float amount) {
        linkStrength = Math.min(100f, linkStrength + Math.max(0f, amount));
    }

    public boolean ended() {
        return ended;
    }

    public void markEnded() {
        ended = true;
    }

    public long lastActionGameTime() {
        return lastActionGameTime;
    }

    public void touch(long gameTime) {
        this.lastActionGameTime = gameTime;
    }

    public int interruptionsThisPhase() {
        return interruptionsThisPhase;
    }

    public void addInterruption() {
        interruptionsThisPhase++;
    }

    /** A member is "downed" once their own pool gives out - they can still be Revived by a teammate while the fight continues. */
    public boolean isDowned(UUID memberId) {
        MindCombatant combatant = defenders.get(memberId);
        return combatant == null || combatant.isBroken();
    }

    /** The whole team loses once every single member is downed - a lone conscious defender can still fight on. */
    public boolean isTeamDefeated() {
        for (UUID memberId : defenders.keySet()) {
            if (!isDowned(memberId)) {
                return false;
            }
        }
        return true;
    }
}
