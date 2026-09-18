package com.dragonspeech.mind;

import java.util.UUID;

/**
 * Live state for exactly one ongoing duel between an attacker and a
 * defender. Deliberately mutable (unlike most of this codebase's data
 * records) - a duel changes many small numbers many times per second
 * while its screens are open, and wrapping every tick in a fresh
 * immutable copy would fight the rest of this system rather than help it.
 *
 * TWO BARRIERS, symmetric: attackerBreach/attackerBarrierIntegrity is
 * the ATTACKER's own mental barrier (the attacker mends it, the
 * defender cracks it); defenderBreach/defenderBarrierIntegrity is the
 * DEFENDER's own (the defender mends it, the attacker cracks it). Both
 * participants are simultaneously attacking one barrier and defending
 * the other. Whichever barrier reaches 0 first decides the outcome -
 * see MindDuelActionService.afterBarrierChange(). If the DEFENDER's
 * barrier breaks first, this plays out exactly as it always has
 * (attacker connects to defender). If the ATTACKER's barrier breaks
 * first, roles swap via swapRolesForVictory() and the (former) defender
 * connects to the (former) attacker instead - the defender can now
 * actually win, not just stall.
 *
 * attackerId/defenderId/attacker/defender/defenderTier are NOT final -
 * see swapRolesForVictory().
 *
 * pendingRoleChoice/SEIZE_CONTROL are KEPT but no longer triggered by
 * the normal flow - a Focus bar hitting 0 now just means THAT SIDE'S
 * OWN barrier instantly collapses (see MindDuelActionService's Focus
 * handling), which flows through the same unified win-check as normal
 * barrier depletion. The old "defender gets a choice: flee or seize
 * control" mid-fight mechanic was a workaround for the old one-barrier,
 * one-directional design; the symmetric two-barrier system already
 * gives the defender a real way to win, so that workaround is no
 * longer needed. Left in place rather than torn out across every file
 * that touches it, to avoid the risk that comes with a wide removal.
 *
 * controlAdvantage: -100 (defender fully in control) to +100 (attacker
 * fully in control). Currently displayed during OCCUPIED_MIND.
 */
public final class ActiveMindDuel {

    private final UUID duelId;
    private UUID attackerId;
    private UUID defenderId;
    private MindCombatant attacker;
    private MindCombatant defender;
    private SentienceTier defenderTier;

    private DuelPhase phase;
    private float attackerBarrierIntegrity = 100f;
    private float defenderBarrierIntegrity = 100f;
    private float controlAdvantage = 0f;
    private long lastActionGameTime;
    private int interruptionsThisPhase = 0;
    private BreachState attackerBreach;
    private BreachState defenderBreach;
    /** Dormant - see class doc. Kept only so SEIZE_CONTROL/DISENGAGE-while-pending code elsewhere still compiles and behaves sanely if ever reached. */
    private boolean pendingRoleChoice = false;
    /** True once the attacker has correctly guessed the defender's true name - unlocks permanent-mode behavior (Control has no periodic skill check, stamina interference doesn't auto-expire) rather than the normal, contestable Occupied Mind access. */
    private boolean trueNameKnown = false;

    public ActiveMindDuel(UUID attackerId, UUID defenderId, MindCombatant attacker, MindCombatant defender, SentienceTier defenderTier) {
        this.duelId = UUID.randomUUID();
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.attacker = attacker;
        this.defender = defender;
        this.defenderTier = defenderTier;
        this.phase = DuelPhase.DEFENSE_BREACH; // contact has already succeeded by the time this object exists
        this.attackerBreach = new BreachState();
        this.defenderBreach = new BreachState();
    }

    public UUID duelId() {
        return duelId;
    }

    public UUID attackerId() {
        return attackerId;
    }

    public UUID defenderId() {
        return defenderId;
    }

    public MindCombatant attacker() {
        return attacker;
    }

    public MindCombatant defender() {
        return defender;
    }

    public SentienceTier defenderTier() {
        return defenderTier;
    }

    public DuelPhase phase() {
        return phase;
    }

    public void setPhase(DuelPhase phase) {
        this.phase = phase;
        this.interruptionsThisPhase = 0;
    }

    public float attackerBarrierIntegrity() {
        return attackerBarrierIntegrity;
    }

    public void damageAttackerBarrier(float amount) {
        attackerBarrierIntegrity = Math.max(0f, attackerBarrierIntegrity - Math.max(0f, amount));
    }

    public void repairAttackerBarrier(float amount) {
        attackerBarrierIntegrity = Math.min(100f, attackerBarrierIntegrity + Math.max(0f, amount));
    }

    public float defenderBarrierIntegrity() {
        return defenderBarrierIntegrity;
    }

    public void damageDefenderBarrier(float amount) {
        defenderBarrierIntegrity = Math.max(0f, defenderBarrierIntegrity - Math.max(0f, amount));
    }

    public void repairDefenderBarrier(float amount) {
        defenderBarrierIntegrity = Math.min(100f, defenderBarrierIntegrity + Math.max(0f, amount));
    }

    public float controlAdvantage() {
        return controlAdvantage;
    }

    public void shiftControlToward(float delta) {
        controlAdvantage = Math.max(-100f, Math.min(100f, controlAdvantage + delta));
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

    /** Which side a given participant UUID plays - used by resolvers so a single action handler works for both roles. */
    public boolean isAttacker(UUID entityId) {
        return attackerId.equals(entityId);
    }

    /** The attacker's own barrier - the attacker mends it (SEAL_CRACK), the defender cracks it (STRIKE_CRACK). */
    public BreachState attackerBreach() {
        return attackerBreach;
    }

    /** The defender's own barrier - the defender mends it (SEAL_CRACK), the attacker cracks it (STRIKE_CRACK). */
    public BreachState defenderBreach() {
        return defenderBreach;
    }

    public boolean pendingRoleChoice() {
        return pendingRoleChoice;
    }

    public void setPendingRoleChoice(boolean value) {
        this.pendingRoleChoice = value;
    }

    public boolean trueNameKnown() {
        return trueNameKnown;
    }

    public void setTrueNameKnown(boolean value) {
        this.trueNameKnown = value;
    }

    /**
     * Dormant, see class doc - SEIZE_CONTROL's old mid-fight role
     * reversal (resets a fresh single BreachState and a moderate
     * integrity to keep fighting). Kept only for compile-compatibility
     * with existing callers; the normal win path now goes through
     * swapRolesForVictory() instead, which doesn't reset anything since
     * the fight is already over by the time it's called.
     */
    public void swapRoles(long now) {
        UUID formerAttackerId = attackerId;
        MindCombatant formerAttacker = attacker;

        attackerId = defenderId;
        attacker = defender;
        defenderId = formerAttackerId;
        defender = formerAttacker;

        attacker.restoreFocus(attacker.maxFocus() * 0.4f);
        attackerBreach = new BreachState();
        defenderBreach = new BreachState();
        attackerBarrierIntegrity = 55f;
        defenderBarrierIntegrity = 100f;
        pendingRoleChoice = false;
        touch(now);
    }

    /**
     * The defender broke the ATTACKER's barrier first - a real win, per
     * the current design. Swaps which UUID/combatant is "the attacker"
     * so the winner is the one who ends up with post-access options in
     * Occupied Mind, WITHOUT touching breach state at all - the barrier
     * fight is over by the time this runs, only identities need to
     * flip.
     */
    public void swapRolesForVictory(long now) {
        UUID formerAttackerId = attackerId;
        MindCombatant formerAttacker = attacker;
        SentienceTier formerDefenderTier = defenderTier;

        attackerId = defenderId;
        attacker = defender;
        defenderId = formerAttackerId;
        defender = formerAttacker;
        defenderTier = SentienceTier.SIMPLE; // corrected right after by the caller via setDefenderTier() once the new defender's real entity is known

        touch(now);
    }

    public void setDefenderTier(SentienceTier tier) {
        this.defenderTier = tier;
    }
}
