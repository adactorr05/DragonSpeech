package com.dragonspeech.engine;

import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.Optional;

/**
 * Where "marka blidr"/"marka illr" actually lives. Thin wrapper around
 * EntityMarkAttachments, the same "one file touches the attachment API
 * directly, everything else goes through here" split StaminaAccess
 * already uses for player stamina - see that class's own doc for why.
 *
 * Persistent now (a real Fabric data attachment, survives world save/
 * load) - the earlier version of this class was a plain in-memory map
 * that forgot every mark on server restart, which doesn't fit "a
 * lasting sign" at all. That was a real limitation, not a stylistic
 * choice, and this replaces it rather than layering on top of it.
 */
public final class MarkRegistry {

    private MarkRegistry() {}

    public static void set(Entity entity, EntityMark mark) {
        entity.setAttached(EntityMarkAttachments.MARK, mark.name());
    }

    public static void clear(Entity entity) {
        entity.setAttached(EntityMarkAttachments.MARK, null);
    }

    public static Optional<EntityMark> get(Entity entity) {
        String raw = entity.getAttached(EntityMarkAttachments.MARK);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(EntityMark.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static boolean isGood(Entity entity) {
        return get(entity).map(m -> m == EntityMark.GOOD).orElse(false);
    }

    public static boolean isBad(Entity entity) {
        return get(entity).map(m -> m == EntityMark.BAD).orElse(false);
    }
}
