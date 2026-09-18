package com.dragonspeech.network;

import com.dragonspeech.DragonSpeech;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Server -> client: open the Dragon Heart screen (the "rundown version
 * of the bonded screen" per explicit direction) for a specific heart
 * stack, identified by its stable ELDUNARI_ID rather than a slot index -
 * slot indices can shift if the inventory reorders, but the ID doesn't.
 *
 * Carries a full snapshot of the heart's current state so the screen can
 * render immediately without a second round-trip. colorName is "mad"
 * for the mad variant rather than a separate boolean field - folded in
 * to keep this at 6 codec pairs, since StreamCodec.composite's standard
 * overloads only go up to 6.
 */
public record OpenDragonHeartScreenPayload(
    UUID heartId,
    String colorName,
    float energy,
    float maxEnergy,
    boolean useStamina,
    boolean staminaBeforeOwn
) implements CustomPacketPayload {

    public static final Type<OpenDragonHeartScreenPayload> TYPE = new Type<>(DragonSpeech.id("open_dragon_heart_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDragonHeartScreenPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, OpenDragonHeartScreenPayload::heartId,
        ByteBufCodecs.STRING_UTF8, OpenDragonHeartScreenPayload::colorName,
        ByteBufCodecs.FLOAT, OpenDragonHeartScreenPayload::energy,
        ByteBufCodecs.FLOAT, OpenDragonHeartScreenPayload::maxEnergy,
        ByteBufCodecs.BOOL, OpenDragonHeartScreenPayload::useStamina,
        ByteBufCodecs.BOOL, OpenDragonHeartScreenPayload::staminaBeforeOwn,
        OpenDragonHeartScreenPayload::new
    );

    public boolean mad() {
        return "mad".equals(colorName);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
