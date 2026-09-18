package com.dragonspeech.stamina;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A player's current magical resource pool. Deliberately minimal for now -
 * just current/max stamina. Domain Attunement (Phase 4) and Eldunari-style
 * external reservoirs (Phase 4) will extend this record later; keeping it
 * small now means the DrainResolver doesn't need to know about systems
 * that don't exist yet.
 */
public record PlayerMagicData(float stamina, float maxStamina) {

    // Deliberately small - a new speaker of the old tongue has almost
    // nothing to give, exactly like the source material. Growth comes
    // from vocabulary milestones (StaminaMilestones), not from time.
    public static final float DEFAULT_MAX_STAMINA = 20f;

    public static final Codec<PlayerMagicData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.FLOAT.fieldOf("stamina").forGetter(PlayerMagicData::stamina),
        Codec.FLOAT.fieldOf("max_stamina").forGetter(PlayerMagicData::maxStamina)
    ).apply(instance, PlayerMagicData::new));

    public static PlayerMagicData initial() {
        return new PlayerMagicData(DEFAULT_MAX_STAMINA, DEFAULT_MAX_STAMINA);
    }

    /** Returns a new instance with stamina clamped to [0, maxStamina]. Records are immutable - this never mutates the original. */
    public PlayerMagicData withStamina(float newStamina) {
        return new PlayerMagicData(Math.max(0f, Math.min(newStamina, maxStamina)), maxStamina);
    }

    public PlayerMagicData withMaxStamina(float newMax) {
        return new PlayerMagicData(Math.min(stamina, newMax), newMax);
    }
}
