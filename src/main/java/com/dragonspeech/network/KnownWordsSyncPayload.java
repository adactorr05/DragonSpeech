package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the player's current known-word list, as a JSON blob.
 *
 * BUG FIX: ByteBufCodecs.STRING_UTF8 is vanilla's stringUtf8(32767) - a
 * 32,767 CHARACTER cap baked into the codec instance itself, not a
 * generic "big packet" limit. At 166 known words (e.g. right after a
 * `grantall`) the full per-word JSON blob comfortably exceeds that on
 * its own, and the connection is dropped with an EncoderException
 * ("String too big") the moment that sync tries to send - this would
 * eventually have been hit through completely normal play too, grantall
 * just got there first. Swapped to ByteBufCodecs.stringUtf8(int), the
 * same underlying codec with an explicit, much higher cap - 2,000,000
 * characters comfortably covers many thousands of known words with a
 * lot of headroom, while staying just as simple/compile-safe as the
 * original (no structured list codec, no version-risk collection API).
 *
 * IMPLEMENTATION NOTE (unchanged from before): still deliberately a
 * single JSON string rather than a structured list-of-records
 * StreamCodec, for the same reason as always - collection-codec factory
 * methods are the most version-churned networking API surface in this
 * project. This fix only raises the string's own length ceiling; it
 * doesn't change that underlying design tradeoff.
 *
 * VERSION-RISK NOTE (remaining, smaller): CustomPacketPayload's Type/
 * StreamCodec registration shape (post-1.20.5) is still the newest part
 * of Minecraft's networking. If this doesn't compile, check
 * CustomPacketPayload and StreamCodec in your decompiled sources.
 */
public record KnownWordsSyncPayload(String wordsJson) implements CustomPacketPayload {

    public static final Type<KnownWordsSyncPayload> TYPE = new Type<>(DragonSpeech.id("known_words_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KnownWordsSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(2_000_000), KnownWordsSyncPayload::wordsJson,
            KnownWordsSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}