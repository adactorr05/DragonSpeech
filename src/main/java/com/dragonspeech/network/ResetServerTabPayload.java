package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "Reset This Page" while on the config GUI's Server tab - restores the 10
 * GUI-added Server-tab fields to their defaults (rebond/heart-stamina/extra multipliers/pvp toggles/
 * loot/hatch speed). Does NOT touch difficulty tuning or sentience overrides - see
 * DragonSpeechConfig#resetServerExtrasToDefaults()'s own doc for why those reset independently.
 * Re-validated server-side against hasPermissions(4), same as every other Server-tab edit.
 */
public record ResetServerTabPayload() implements CustomPacketPayload {

    public static final Type<ResetServerTabPayload> TYPE = new Type<>(DragonSpeech.id("reset_server_tab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResetServerTabPayload> STREAM_CODEC =
        StreamCodec.unit(new ResetServerTabPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
