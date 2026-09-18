package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client -> server: the "Give Heart" button on the Dragon Bond screen -
 * "replaced with a button on the bonded dragon gui that is there when
 * the heart is there and is gone when the heart has been given. When
 * you press the button, you are given that dragon's heart" per explicit
 * direction. Replaces the old repeatable "/dragon eldunari" command
 * entirely (removed) - re-validated server-side exactly like
 * SetDragonNamePayload/DragonBondSettingsPayload are: the dragon must
 * actually be bonded to the sending player AND not have already given
 * its one heart (DragonEntity.heartGiven()), regardless of what the
 * packet claims.
 */
public record GiveDragonHeartPayload(UUID dragonId) implements CustomPacketPayload {

    public static final Type<GiveDragonHeartPayload> TYPE = new Type<>(DragonSpeech.id("give_dragon_heart"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GiveDragonHeartPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, GiveDragonHeartPayload::dragonId,
        GiveDragonHeartPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
