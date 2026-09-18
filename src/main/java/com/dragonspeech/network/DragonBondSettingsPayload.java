package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client -> server: the full settings state from the Dragon Bond
 * screen, sent whenever the player changes any control. The server
 * handler (see DragonSpeechNetworking) re-validates the sender actually
 * owns the target dragon before applying anything, regardless of what's
 * in the packet.
 *
 * Packed into ONE int rather than five separate StreamCodec.composite
 * fields - this project's other payloads top out at 3 composite fields
 * (see ContactBeamPayload), and I don't have a decompiled jar on hand
 * to confirm composite() supports more than that in this exact version.
 * Packing to (UUID, VAR_INT) - 2 fields - stays safely within what's
 * already proven to compile here (see TeamActionPayload, also 2
 * fields), at the cost of a small amount of bit-twiddling below instead
 * of trusting an unverified higher arity.
 */
public record DragonBondSettingsPayload(UUID dragonId, int packedSettings) implements CustomPacketPayload {

    public static final Type<DragonBondSettingsPayload> TYPE = new Type<>(DragonSpeech.id("dragon_bond_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DragonBondSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, DragonBondSettingsPayload::dragonId,
        ByteBufCodecs.VAR_INT, DragonBondSettingsPayload::packedSettings,
        DragonBondSettingsPayload::new
    );

    private static final int BIT_USE_DRAGON_STAMINA = 1;
    private static final int BIT_STAMINA_BEFORE_OWN = 1 << 1;
    private static final int BIT_OPTION_FOLLOWING = 1 << 2;
    private static final int BIT_OPTION_AGGRESSIVE_ASSIST = 1 << 3;
    private static final int BIT_ATTACK_NEARBY_HOSTILE = 1 << 4;
    private static final int BIT_ATTACK_NEARBY_NEUTRAL = 1 << 5;
    private static final int BIT_ATTACK_NEARBY_PASSIVE = 1 << 6;
    private static final int BIT_OPTION_STAY = 1 << 7;
    private static final int LIMITER_SHIFT = 8; // bits 8-14: staminaLimiterPercent (0-100 fits in 7 bits)

    public static DragonBondSettingsPayload pack(UUID dragonId, boolean useDragonStamina, boolean staminaBeforeOwn,
                                                  int staminaLimiterPercent, boolean optionFollowing, boolean optionAggressiveAssist,
                                                  boolean attackNearbyHostile, boolean attackNearbyNeutral, boolean attackNearbyPassive,
                                                  boolean optionStay) {
        int bits = 0;
        if (useDragonStamina) bits |= BIT_USE_DRAGON_STAMINA;
        if (staminaBeforeOwn) bits |= BIT_STAMINA_BEFORE_OWN;
        if (optionFollowing) bits |= BIT_OPTION_FOLLOWING;
        if (optionAggressiveAssist) bits |= BIT_OPTION_AGGRESSIVE_ASSIST;
        if (attackNearbyHostile) bits |= BIT_ATTACK_NEARBY_HOSTILE;
        if (attackNearbyNeutral) bits |= BIT_ATTACK_NEARBY_NEUTRAL;
        if (attackNearbyPassive) bits |= BIT_ATTACK_NEARBY_PASSIVE;
        if (optionStay) bits |= BIT_OPTION_STAY;
        bits |= (Math.max(0, Math.min(100, staminaLimiterPercent)) << LIMITER_SHIFT);
        return new DragonBondSettingsPayload(dragonId, bits);
    }

    public boolean useDragonStamina() {
        return (packedSettings & BIT_USE_DRAGON_STAMINA) != 0;
    }

    public boolean staminaBeforeOwn() {
        return (packedSettings & BIT_STAMINA_BEFORE_OWN) != 0;
    }

    public boolean optionFollowing() {
        return (packedSettings & BIT_OPTION_FOLLOWING) != 0;
    }

    public boolean optionAggressiveAssist() {
        return (packedSettings & BIT_OPTION_AGGRESSIVE_ASSIST) != 0;
    }

    public boolean attackNearbyHostile() {
        return (packedSettings & BIT_ATTACK_NEARBY_HOSTILE) != 0;
    }

    public boolean attackNearbyNeutral() {
        return (packedSettings & BIT_ATTACK_NEARBY_NEUTRAL) != 0;
    }

    public boolean attackNearbyPassive() {
        return (packedSettings & BIT_ATTACK_NEARBY_PASSIVE) != 0;
    }

    public boolean optionStay() {
        return (packedSettings & BIT_OPTION_STAY) != 0;
    }

    public int staminaLimiterPercent() {
        return (packedSettings >> LIMITER_SHIFT) & 0x7F;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
