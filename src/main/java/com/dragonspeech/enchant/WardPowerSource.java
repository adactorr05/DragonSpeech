package com.dragonspeech.enchant;

/**
 * Sustain source for item-bound wards.
 * DURATION is the unmodified default; RESERVE is created by `afla`; STAMINA_LINKED is `aflbinda`.
 * DURABILITY remains only as a legacy save value and behaves like RESERVE.
 */
public enum WardPowerSource {
    DURATION,
    RESERVE,
    STAMINA_LINKED,
    DURABILITY
}
