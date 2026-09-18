package com.dragonspeech.engine;

import com.dragonspeech.DragonSpeech;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Registers a mark (GOOD/BAD) as a persistent Fabric data attachment on
 * an entity - "lasting sign" means surviving a server restart, which the
 * first version of MarkRegistry (a plain in-memory map) deliberately
 * didn't do. This is the exact same registration shape
 * PlayerMagicAttachments already uses successfully for player stamina in
 * this project, just attached to entities generally instead of players
 * specifically (Fabric's attachment API targets any Entity the same
 * way - nothing here is player-only).
 *
 * Stored as a plain String (the enum's name()) rather than a bespoke
 * EntityMark codec - Codec.STRING is about as low-risk as a codec gets,
 * and MarkAccess.get() below already has to tolerate a missing/garbled
 * value gracefully regardless.
 *
 * VERSION-RISK NOTE: see PlayerMagicAttachments's own note - same API,
 * same caveat. If THIS specific attachment fails to compile/register
 * while PlayerMagicAttachments still works, the most likely cause is
 * this project's Fabric API version scoping attachments to specific
 * target types at registration (i.e. requiring something like
 * `.target(EntityType...)` that PlayerMagicAttachments didn't need
 * because it only ever targeted players) - check AttachmentRegistry's
 * builder methods in your IDE for anything like that if so.
 */
public final class EntityMarkAttachments {

    private EntityMarkAttachments() {}

    public static final AttachmentType<String> MARK = AttachmentRegistry.<String>builder()
        .copyOnDeath()
        .persistent(Codec.STRING)
        .buildAndRegister(DragonSpeech.id("entity_mark"));

    /** Call from onInitialize() to force this class's static fields (and therefore registration) to run. */
    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}
