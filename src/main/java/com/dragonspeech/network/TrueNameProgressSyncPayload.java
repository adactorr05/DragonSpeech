package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the receiving player's OWN true-name progress list (the Grimoire's "true names" tab), same "JSON string over the wire" shape as WardSyncPayload/SkillsSyncPayload/ScarSyncPayload. */
public record TrueNameProgressSyncPayload(String progressJson) implements CustomPacketPayload {

    public static final Type<TrueNameProgressSyncPayload> TYPE = new Type<>(DragonSpeech.id("true_name_progress_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrueNameProgressSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TrueNameProgressSyncPayload::progressJson,
        TrueNameProgressSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
