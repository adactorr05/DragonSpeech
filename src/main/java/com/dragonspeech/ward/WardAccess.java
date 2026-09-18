package com.dragonspeech.ward;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Same pattern and same version-risk as the other attachments - see
 * PlayerMagicAttachments for the full explanation and fallback.
 *
 * WIDENED from ServerPlayer to LivingEntity per explicit direction ("I
 * should be able to ward any entity unless it has a ward that wards
 * against magic") - Fabric's attachment API (getAttached/setAttached)
 * already works on any Entity, not just Player; ServerPlayer here was
 * just a narrower constraint than the underlying mechanism needed,
 * never a technical requirement. See WardService's own doc for what
 * else had to widen alongside this.
 */
public final class WardAccess {

    private static final AttachmentType<PlayerWards> WARDS = AttachmentRegistry.<PlayerWards>builder()
        .persistent(PlayerWards.CODEC)
        .initializer(PlayerWards::empty)
        .buildAndRegister(DragonSpeech.id("wards"));

    private WardAccess() {}

    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }

    public static PlayerWards get(LivingEntity entity) {
        PlayerWards data = entity.getAttached(WARDS);
        return data != null ? data : PlayerWards.empty();
    }

    public static void set(LivingEntity entity, PlayerWards wards) {
        entity.setAttached(WARDS, wards);
    }
}
