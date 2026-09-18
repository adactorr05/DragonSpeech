package com.dragonspeech.dragon;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Registers PlayerBondData as a persistent Fabric data attachment -
 * same pattern as PlayerMagicAttachments (stamina), see that class for
 * the version-risk note on the Attachment API's exact shape, which
 * applies equally here.
 *
 * Deliberately NOT .copyOnDeath() - unlike stamina, bond data should
 * absolutely NOT reset or transfer on the PLAYER's own death (dying
 * yourself has nothing to do with whether your dragon is still alive).
 */
public final class PlayerBondAttachments {

    private PlayerBondAttachments() {}

    public static final AttachmentType<PlayerBondData> BOND_DATA = AttachmentRegistry.<PlayerBondData>builder()
        .persistent(PlayerBondData.CODEC)
        .initializer(PlayerBondData::initial)
        .buildAndRegister(DragonSpeech.id("bond_data"));

    /** Call from onInitialize() to force this class's static fields (and therefore registration) to run. */
    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}
