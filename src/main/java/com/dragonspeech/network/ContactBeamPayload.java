package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: tells the attacker's own client to render (or stop
 * rendering) the Contact "reaching out" beam - a real traveling line
 * toward the target, not a particle trail.
 *
 * Deliberately carries only a relative durationTicks, not an absolute
 * game-time - the client starts its own local timer the instant this
 * arrives. The beam is a best-effort visual flourish; the server's own
 * PendingContact remains the sole source of truth for when Contact
 * actually resolves, so the client's timer never needs to be perfectly
 * synced to it. This also sidesteps needing a long-typed StreamCodec
 * field, which has no precedent elsewhere in this codebase's payloads
 * (only VAR_INT/BOOL/STRING_UTF8/FLOAT do) - every field here is a plain
 * int, the safest, most-proven type available.
 *
 * `canceled` true means stop immediately (the attempt resolved or was
 * interrupted); otherwise the client renders toward the entity with
 * network id `targetEntityId` for `durationTicks`.
 */
public record ContactBeamPayload(int targetEntityId, int durationTicks, boolean canceled) implements CustomPacketPayload {

    public static final Type<ContactBeamPayload> TYPE = new Type<>(DragonSpeech.id("contact_beam"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContactBeamPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ContactBeamPayload::targetEntityId,
        ByteBufCodecs.VAR_INT, ContactBeamPayload::durationTicks,
        ByteBufCodecs.BOOL, ContactBeamPayload::canceled,
        ContactBeamPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
