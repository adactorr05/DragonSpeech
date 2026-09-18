package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "spend some Stamina to shorten my in-flight Contact attempt's remaining travel time." Sent repeatedly at a throttled rate while the player holds the reach-out key during an active reach. No fields needed - at most one pending contact exists per player. */
public record HastenContactPayload() implements CustomPacketPayload {

    public static final Type<HastenContactPayload> TYPE = new Type<>(DragonSpeech.id("hasten_contact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HastenContactPayload> STREAM_CODEC =
        StreamCodec.unit(new HastenContactPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
