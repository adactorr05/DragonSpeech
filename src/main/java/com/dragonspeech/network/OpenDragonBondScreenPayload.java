package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Server -> client: open the Dragon Bond screen for the dragon with this UUID - sent right after a successful egg-bonding, per the design notes ("When you hatch an egg, your dragon becomes bonded to you. This should open up a screen"). */
public record OpenDragonBondScreenPayload(UUID dragonId) implements CustomPacketPayload {

    public static final Type<OpenDragonBondScreenPayload> TYPE = new Type<>(DragonSpeech.id("open_dragon_bond_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDragonBondScreenPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, OpenDragonBondScreenPayload::dragonId,
        OpenDragonBondScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
