package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client -> server: change a specific heart's stamina settings, from the Dragon Heart screen. Re-validated server-side (the heart must actually be found, usable, and belong to the sending player's own inventory) - never trusted blindly from the packet. */
public record SetHeartStaminaSettingsPayload(UUID heartId, boolean useStamina, boolean staminaBeforeOwn) implements CustomPacketPayload {

    public static final Type<SetHeartStaminaSettingsPayload> TYPE = new Type<>(DragonSpeech.id("set_heart_stamina_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetHeartStaminaSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, SetHeartStaminaSettingsPayload::heartId,
        ByteBufCodecs.BOOL, SetHeartStaminaSettingsPayload::useStamina,
        ByteBufCodecs.BOOL, SetHeartStaminaSettingsPayload::staminaBeforeOwn,
        SetHeartStaminaSettingsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
