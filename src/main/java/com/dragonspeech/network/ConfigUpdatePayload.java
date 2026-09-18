package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "apply this Server-tab state." Sent every time a
 * control on the Server tab changes, carrying the WHOLE current
 * Server-tab snapshot (same "send the full state on every change"
 * convention DragonBondSettingsPayload already uses - see that file's
 * own doc).
 *
 * Re-validated server-side (see ConfigRequestHandler#handleUpdate) -
 * the sender's REAL permission level is checked again on receipt,
 * exactly like every other C2S payload in this project. A client with
 * the Server tab hidden (no permission) has no UI path that could even
 * build this payload, but a modified/hostile client could still send
 * one directly - the server never trusts it without re-checking
 * hasPermissions(4) itself first.
 */
public record ConfigUpdatePayload(String configJson) implements CustomPacketPayload {

    public static final Type<ConfigUpdatePayload> TYPE = new Type<>(DragonSpeech.id("config_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32_768), ConfigUpdatePayload::configJson,
            ConfigUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
