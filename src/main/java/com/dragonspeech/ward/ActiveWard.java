package com.dragonspeech.ward;

import com.dragonspeech.spell.SustainMode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/**
 * One active player/entity ward.
 *
 * Sustain is deliberately exclusive:
 *  - DURATION: no durability pool is spent; the ward exists until expiresAt.
 *  - RESERVE (`afla`): remainingEnergy is the ward's own reserve and is spent by blocks.
 *  - CASTER (`aflbinda`): blocks are paid directly from the original caster's stamina.
 *
 * maxCharges/chargesUsed remain for save compatibility and statistics, but are not a second
 * hidden durability system.
 */
public record ActiveWard(
    UUID id,
    UUID casterId,
    WardType type,
    float remainingEnergy,
    float maxEnergy,
    int maxCharges,
    int chargesUsed,
    boolean visibleToOthers,
    boolean staminaBound,
    SustainMode sustainMode,
    long expiresAt
) {
    public static final Codec<ActiveWard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(ActiveWard::id),
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("caster_id").forGetter(ActiveWard::casterId),
        WardType.CODEC.fieldOf("type").forGetter(ActiveWard::type),
        Codec.FLOAT.fieldOf("remaining_energy").forGetter(ActiveWard::remainingEnergy),
        Codec.FLOAT.optionalFieldOf("max_energy", -1f).forGetter(ActiveWard::maxEnergy),
        Codec.INT.fieldOf("max_charges").forGetter(ActiveWard::maxCharges),
        Codec.INT.fieldOf("charges_used").forGetter(ActiveWard::chargesUsed),
        Codec.BOOL.optionalFieldOf("visible_to_others", true).forGetter(ActiveWard::visibleToOthers),
        Codec.BOOL.optionalFieldOf("stamina_bound", false).forGetter(ActiveWard::staminaBound),
        Codec.STRING.optionalFieldOf("sustain_mode", "legacy").forGetter(w -> w.sustainMode().name().toLowerCase(java.util.Locale.ROOT)),
        Codec.LONG.optionalFieldOf("expires_at", -1L).forGetter(ActiveWard::expiresAt)
    ).apply(instance, (id, casterId, type, remainingEnergy, maxEnergy, maxCharges, chargesUsed,
                       visibleToOthers, staminaBound, modeName, expiresAt) -> {
        SustainMode mode;
        try {
            mode = "legacy".equalsIgnoreCase(modeName)
                ? (staminaBound ? SustainMode.CASTER : SustainMode.RESERVE)
                : SustainMode.valueOf(modeName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            mode = staminaBound ? SustainMode.CASTER : SustainMode.RESERVE;
        }
        return new ActiveWard(id, casterId, type, remainingEnergy,
            maxEnergy > 0f ? maxEnergy : Math.max(remainingEnergy, 1f),
            maxCharges, chargesUsed, visibleToOthers, mode == SustainMode.CASTER, mode, expiresAt);
    }));

    /** Compatibility constructor for older call sites/saved semantics. */
    public ActiveWard(UUID id, UUID casterId, WardType type, float remainingEnergy, float maxEnergy,
                      int maxCharges, int chargesUsed, boolean visibleToOthers, boolean staminaBound) {
        this(id, casterId, type, remainingEnergy, maxEnergy, maxCharges, chargesUsed,
            visibleToOthers, staminaBound,
            staminaBound ? SustainMode.CASTER : SustainMode.RESERVE, -1L);
    }

    public boolean isExpired(long gameTime) {
        return sustainMode == SustainMode.DURATION && expiresAt >= 0L && gameTime >= expiresAt;
    }

    public boolean isBroken() {
        return sustainMode == SustainMode.RESERVE && remainingEnergy <= 0f;
    }

    public ActiveWard afterAbsorbing(float amount) {
        float remaining = sustainMode == SustainMode.RESERVE
            ? Math.max(0f, remainingEnergy - Math.max(0f, amount))
            : remainingEnergy;
        return new ActiveWard(id, casterId, type, remaining, maxEnergy,
            maxCharges, chargesUsed + 1, visibleToOthers, sustainMode == SustainMode.CASTER,
            sustainMode, expiresAt);
    }
}
