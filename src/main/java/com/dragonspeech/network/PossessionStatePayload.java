package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> possessing client: which mob's skin the player currently
 * wears (or -1/false when a possession ends). The client uses this
 * purely for presentation - PossessedMobRenderMixin skips rendering
 * that one entity in first person so the borrowed skin doesn't sit
 * inside the camera. All actual possession logic is server-side in
 * PossessionService.
 */
public record PossessionStatePayload(int mobEntityId, boolean active) implements CustomPacketPayload {

    public static final Type<PossessionStatePayload> TYPE = new Type<>(DragonSpeech.id("possession_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PossessionStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PossessionStatePayload::mobEntityId,
        ByteBufCodecs.BOOL, PossessionStatePayload::active,
        PossessionStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
