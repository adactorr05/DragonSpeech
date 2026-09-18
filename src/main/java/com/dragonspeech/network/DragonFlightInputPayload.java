package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: flight input while riding a dragon. "Double press
 * space to fly, control to go down, double press W to fly faster (like
 * sprinting)" - keyboard state only exists client-side, so this is how
 * the server finds out what the rider actually wants. wantsAscend/
 * wantsDescend/holdingUp/sprintFlying are sent only when they CHANGE
 * (not every tick). jumpRequested is different - a one-shot momentary
 * trigger ("press once = jump"), sent exactly once per confirmed single
 * tap and never persisted as state.
 *
 * sprintFlying mirrors wantsAscend's own shape exactly - a genuine
 * double-tap TOGGLE (of the forward key, not space), not a momentary
 * "is W held" state. Unlike holdingUp (which specifically had to be
 * separated from a toggle to fix runaway climbing - see that field's
 * own doc), a toggle is the right shape here: "fly faster" is a mode
 * you turn on, not something that should stop the instant you release
 * forward mid-flight.
 */
public record DragonFlightInputPayload(boolean wantsAscend, boolean wantsDescend, boolean holdingUp, boolean sprintFlying, boolean jumpRequested) implements CustomPacketPayload {

    public static final Type<DragonFlightInputPayload> TYPE = new Type<>(DragonSpeech.id("dragon_flight_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DragonFlightInputPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, DragonFlightInputPayload::wantsAscend,
        ByteBufCodecs.BOOL, DragonFlightInputPayload::wantsDescend,
        ByteBufCodecs.BOOL, DragonFlightInputPayload::holdingUp,
        ByteBufCodecs.BOOL, DragonFlightInputPayload::sprintFlying,
        ByteBufCodecs.BOOL, DragonFlightInputPayload::jumpRequested,
        DragonFlightInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
