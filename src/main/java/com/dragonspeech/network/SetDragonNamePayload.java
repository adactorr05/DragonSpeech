package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client -> server: rename a bonded dragon (the "choose your dragon's name" field on the Dragon Bond screen). An empty string clears the custom name back to the default "Dragon". */
public record SetDragonNamePayload(UUID dragonId, String name) implements CustomPacketPayload {

    public static final Type<SetDragonNamePayload> TYPE = new Type<>(DragonSpeech.id("set_dragon_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDragonNamePayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, SetDragonNamePayload::dragonId,
        ByteBufCodecs.STRING_UTF8, SetDragonNamePayload::name,
        SetDragonNamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
