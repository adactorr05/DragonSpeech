package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: attempt Contact (Phase 6) against the entity with this network id - whatever the player is currently looking at/targeting. */
public record ReachOutPayload(int targetEntityId) implements CustomPacketPayload {

    public static final Type<ReachOutPayload> TYPE = new Type<>(DragonSpeech.id("reach_out"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReachOutPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ReachOutPayload::targetEntityId,
        ReachOutPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
