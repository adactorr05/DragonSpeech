package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "Clear All Overrides" on the Sentience Editor screen - removes every entity's
 * tier/reaction-power override at once, both in-memory and from sentience.json. Re-validated
 * server-side against hasPermissions(4), same as every other Server-tab edit.
 */
public record ClearSentienceOverridesPayload() implements CustomPacketPayload {

    public static final Type<ClearSentienceOverridesPayload> TYPE = new Type<>(DragonSpeech.id("clear_sentience_overrides"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearSentienceOverridesPayload> STREAM_CODEC =
        StreamCodec.unit(new ClearSentienceOverridesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
