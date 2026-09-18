package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the whole Server-tab state of the config GUI, as a
 * single JSON blob (same "one JSON string" convention as
 * KnownWordsSyncPayload - see that file's own doc for why: this
 * project's networking API keeps churning most on structured
 * list/record codecs, so a plain string sidesteps that entirely).
 *
 * Sent in response to a ConfigRequestPayload (see
 * ConfigScreen/ConfigRequestHandler) - never pushed unprompted, since
 * the config screen is the only thing that ever needs this.
 *
 * canEditJson carries a single boolean ("can_edit": true/false) baked
 * into the SAME json blob rather than a second payload field, computed
 * server-side from the requesting player's REAL permission level
 * (ServerPlayer#hasPermissions(4)) - never trust a client-reported
 * permission flag for this, only what the server itself already knows
 * about that connection.
 */
public record ConfigSyncPayload(String configJson) implements CustomPacketPayload {

    public static final Type<ConfigSyncPayload> TYPE = new Type<>(DragonSpeech.id("config_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32_768), ConfigSyncPayload::configJson,
            ConfigSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
