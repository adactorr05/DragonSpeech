package com.dragonspeech.ward;

import com.dragonspeech.engine.Element;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/** What a ward is keyed to intercept. */
public enum WardType implements StringRepresentable {
    PROJECTILE, EXPLOSION, FALL, MELEE,
    FIRE, LIGHTNING, WIND, ICE, WATER, POISON, FORCE, EARTH, LIGHT, SHADOW, DEATH, LIFE, VOID,
    MAGIC,
    DANGER_LIFSKAD, DANGER_LIFROF, DANGER_LIFSLIT, DANGER_LIFSTILLA, DANGER_LIFTHAGN,
    REVIVAL;

    public static final Codec<WardType> CODEC = StringRepresentable.fromEnum(WardType::values);

    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

    public boolean isDangerWordWard() {
        return this == DANGER_LIFSKAD || this == DANGER_LIFROF || this == DANGER_LIFSLIT
            || this == DANGER_LIFSTILLA || this == DANGER_LIFTHAGN;
    }

    public static Optional<WardType> fromElement(Element element) {
        if (element == null) return Optional.empty();
        return Optional.of(switch (element) {
            case FIRE -> FIRE; case LIGHTNING -> LIGHTNING; case WIND -> WIND; case ICE -> ICE;
            case WATER -> WATER; case POISON -> POISON; case FORCE -> FORCE; case EARTH -> EARTH;
            case LIGHT -> LIGHT; case SHADOW -> SHADOW; case DEATH -> DEATH; case LIFE -> LIFE; case VOID -> VOID;
        });
    }
}
