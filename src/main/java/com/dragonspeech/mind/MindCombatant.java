package com.dragonspeech.mind;

/**
 * One side's live resource pools for the duration of a single duel -
 * NOT the same object as a player's persistent mind profile (see
 * MindFortitudeService) - a fresh snapshot taken when the duel starts,
 * so mid-duel damage/drains never leak back into a player's baseline
 * stats between fights.
 *
 * Five real pools, matching the five cards: Focus, Stamina, Willpower,
 * Power, Speed - each with its own max and its own passive regen rate
 * (see MindDuelTickHandler), all scaled by how strong the combatant is
 * with the mind skill (see MindFortitudeService). Discipline remains a
 * single flat (non-pool) stat - it only ever reduces interruption
 * penalties, it was never one of the five clickable bars.
 *
 * FOCUS is the special one: it is the only pool physical damage taken
 * OUTSIDE the duel can drain (see MindDuelService.onPhysicalDamage),
 * and hitting 0 on EITHER side's Focus ends the encounter immediately
 * rather than just being "a bad pool to be low on" - see
 * MindDuelActionService for exactly what happens for the attacker vs
 * the defender in that case.
 */
public final class MindCombatant {

    private float focus;
    private final float maxFocus;
    private float stamina;
    private final float maxStamina;
    private float willpower;
    private final float maxWillpower;
    private float power;
    private final float maxPower;
    private float speed;
    private final float maxSpeed;
    private final float discipline;

    /** Passive per-second regen for each pool - set once at construction from the entity's overall skill strength. */
    private final float focusRegenPerSecond;
    private final float staminaRegenPerSecond;
    private final float willpowerRegenPerSecond;
    private final float powerRegenPerSecond;
    private final float speedRegenPerSecond;

    public MindCombatant(float maxFocus, float maxStamina, float maxWillpower, float maxPower, float maxSpeed, float discipline,
                          float focusRegenPerSecond, float staminaRegenPerSecond, float willpowerRegenPerSecond,
                          float powerRegenPerSecond, float speedRegenPerSecond) {
        this.focus = maxFocus;
        this.maxFocus = maxFocus;
        this.stamina = maxStamina;
        this.maxStamina = maxStamina;
        this.willpower = maxWillpower;
        this.maxWillpower = maxWillpower;
        this.power = maxPower;
        this.maxPower = maxPower;
        this.speed = maxSpeed;
        this.maxSpeed = maxSpeed;
        this.discipline = discipline;
        this.focusRegenPerSecond = focusRegenPerSecond;
        this.staminaRegenPerSecond = staminaRegenPerSecond;
        this.willpowerRegenPerSecond = willpowerRegenPerSecond;
        this.powerRegenPerSecond = powerRegenPerSecond;
        this.speedRegenPerSecond = speedRegenPerSecond;
    }

    // --- Focus
    public float focus() { return focus; }
    public float maxFocus() { return maxFocus; }
    public void damageFocus(float amount) { focus = Math.max(0f, focus - Math.max(0f, amount)); }
    public void restoreFocus(float amount) { focus = Math.min(maxFocus, focus + Math.max(0f, amount)); }

    // --- Stamina
    public float stamina() { return stamina; }
    public float maxStamina() { return maxStamina; }
    public void drainStamina(float amount) { stamina = Math.max(0f, stamina - Math.max(0f, amount)); }
    public void restoreStamina(float amount) { stamina = Math.min(maxStamina, stamina + Math.max(0f, amount)); }
    public boolean canAffordStamina(float cost) { return stamina >= cost; }

    // --- Willpower
    public float willpower() { return willpower; }
    public float maxWillpower() { return maxWillpower; }
    public void drainWillpower(float amount) { willpower = Math.max(0f, willpower - Math.max(0f, amount)); }
    public void restoreWillpower(float amount) { willpower = Math.min(maxWillpower, willpower + Math.max(0f, amount)); }

    // --- Power
    public float power() { return power; }
    public float maxPower() { return maxPower; }
    public void drainPower(float amount) { power = Math.max(0f, power - Math.max(0f, amount)); }
    public void restorePower(float amount) { power = Math.min(maxPower, power + Math.max(0f, amount)); }

    // --- Speed
    public float speed() { return speed; }
    public float maxSpeed() { return maxSpeed; }
    public void drainSpeed(float amount) { speed = Math.max(0f, speed - Math.max(0f, amount)); }
    public void restoreSpeed(float amount) { speed = Math.min(maxSpeed, speed + Math.max(0f, amount)); }

    // --- Discipline (flat, not a pool)
    public float discipline() { return discipline; }

    public void regenTick(float secondsElapsed) {
        restoreFocus(focusRegenPerSecond * secondsElapsed);
        restoreStamina(staminaRegenPerSecond * secondsElapsed);
        restoreWillpower(willpowerRegenPerSecond * secondsElapsed);
        restorePower(powerRegenPerSecond * secondsElapsed);
        restoreSpeed(speedRegenPerSecond * secondsElapsed);
    }

    /** The current amount of whichever bar a BarType refers to. */
    public float amountOf(BarType bar) {
        return switch (bar) {
            case FOCUS -> focus;
            case STAMINA -> stamina;
            case WILLPOWER -> willpower;
            case POWER -> power;
            case SPEED -> speed;
        };
    }

    public float maxOf(BarType bar) {
        return switch (bar) {
            case FOCUS -> maxFocus;
            case STAMINA -> maxStamina;
            case WILLPOWER -> maxWillpower;
            case POWER -> maxPower;
            case SPEED -> maxSpeed;
        };
    }

    public boolean canAfford(BarType bar, float cost) {
        return amountOf(bar) >= cost;
    }

    /** Spends `cost` from the given bar - caller must have already checked canAfford(). */
    public void spend(BarType bar, float cost) {
        switch (bar) {
            case FOCUS -> damageFocus(cost);
            case STAMINA -> drainStamina(cost);
            case WILLPOWER -> drainWillpower(cost);
            case POWER -> drainPower(cost);
            case SPEED -> drainSpeed(cost);
        }
    }

    /** True the instant EITHER Focus or Stamina hits 0 - Focus hitting 0 is the special "battle ends now" condition (see MindDuelActionService); Stamina hitting 0 is the older "you can only recover" condition, kept for the other three pools' sake even though clicking itself is cheap enough that Stamina running out is rare now. */
    public boolean isBroken() {
        return focus <= 0f || stamina <= 0f;
    }

    /** Specifically Focus - the one pool whose depletion is a hard, immediate end-of-encounter condition rather than just "a bad place to be." */
    public boolean isFocusBroken() {
        return focus <= 0f;
    }

    /**
     * A power-scaled multiplier around 1.0, read from the CURRENT Power
     * pool (not max) - a combatant who has spent their Power hits softer
     * until it recovers. Power 50 gives exactly 1.0x; every 10 points
     * above/below shifts it by 0.1x, floored so a fully-drained Power
     * bar still lands a token amount of damage rather than zero.
     */
    public float powerMultiplier() {
        return Math.max(0.4f, 1f + (power - 50f) / 100f);
    }

    /**
     * Milliseconds to shave off the base click cooldown, read from the
     * CURRENT Speed pool - spending Speed on a card slows your OWN next
     * clicks too, not just the bar's number going down. Speed 50 gives
     * 0; every 10 points above/below shifts it by 40ms.
     */
    public long speedCooldownAdjustMillis() {
        return Math.round((speed - 50f) * 4.0);
    }
}
