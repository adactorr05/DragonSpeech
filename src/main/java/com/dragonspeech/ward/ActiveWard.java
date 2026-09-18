package com.dragonspeech.ward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/**
 * A single ward placed on a player. Wards sit inert (no upkeep cost) until
 * their WardType is actually triggered - that's the whole point of a
 * binding spell versus a channeled one: you pay once up front for
 * standing protection, not per tick for it existing.
 *
 * remainingEnergy is what a triggered hit is absorbed against - if the
 * incoming damage exceeds it, the ward only blocks up to that amount and
 * then breaks. maxEnergy is that same pool's ORIGINAL size, fixed at
 * creation - added per explicit direction ("in the grimoire screen, it
 * should also show what the durability on my ward is... 36/36 and not
 * just 36"). Without a stored max, nothing could ever show "current/max"
 * or compute an accurate ring-brightness fraction - the old fraction
 * math had to guess at a denominator (a flat 40, or whatever was
 * currently left), which is why it never lined up with what actually
 * happened in combat.
 *
 * staminaBound ("aflbinda"): once this ward's stored energy runs out, it
 * does NOT break from that alone - instead, WardService.absorb() starts
 * draining the caster's own stamina (and, if that runs dry, the normal
 * DrainResolver cascade into hunger/health) directly to keep covering
 * hits of its type.
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
    boolean staminaBound
) {
    public static final Codec<ActiveWard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(ActiveWard::id),
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("caster_id").forGetter(ActiveWard::casterId),
        WardType.CODEC.fieldOf("type").forGetter(ActiveWard::type),
        Codec.FLOAT.fieldOf("remaining_energy").forGetter(ActiveWard::remainingEnergy),
        // Wards saved before this field existed decode with max = whatever
        // was left at save time - not perfectly accurate for an old ward
        // that had already taken some hits, but a reasonable one-time
        // fallback, and every ward created from here on always has a real
        // stored max.
        Codec.FLOAT.optionalFieldOf("max_energy", -1f).forGetter(ActiveWard::maxEnergy),
        Codec.INT.fieldOf("max_charges").forGetter(ActiveWard::maxCharges),
        Codec.INT.fieldOf("charges_used").forGetter(ActiveWard::chargesUsed),
        Codec.BOOL.optionalFieldOf("visible_to_others", true).forGetter(ActiveWard::visibleToOthers),
        // Old saved wards from before aflbinda existed simply decode as
        // false here - completely normal, energy-only wards, unchanged.
        Codec.BOOL.optionalFieldOf("stamina_bound", false).forGetter(ActiveWard::staminaBound)
    ).apply(instance, (id, casterId, type, remainingEnergy, maxEnergy, maxCharges, chargesUsed, visibleToOthers, staminaBound) ->
        new ActiveWard(id, casterId, type, remainingEnergy,
            maxEnergy > 0f ? maxEnergy : Math.max(remainingEnergy, 1f),
            maxCharges, chargesUsed, visibleToOthers, staminaBound)));

    /**
     * FIXED: "always 3 hits then breaks, no matter the durability
     * number... mikla raises it from 36 to 57 but still only takes 3
     * hits" per explicit direction - confirmed and root-caused. This
     * used to also check chargesUsed >= maxCharges, and maxCharges is
     * computed from a word's PRECISION alone (1 + round(precision*2)),
     * completely independent of energy/magnitude - for most binding
     * words that works out to exactly 3, no matter how big the energy
     * pool is. A ward would therefore always break on its 3rd
     * absorption regardless of how much energy was actually left,
     * which is exactly the reported behavior.
     *
     * Per explicit direction ("I was hoping [player wards and mob
     * wards] would be the same"), this now matches MobWards.WardInstance
     * exactly - pure durability, no separate hit-count cap. A ward
     * breaks when it's actually out of energy, full stop (unless
     * staminaBound, same as before).
     *
     * maxCharges/chargesUsed stay in the record/codec for save
     * compatibility with existing worlds - just no longer consulted
     * here.
     */
    public boolean isBroken() {
        return !staminaBound && remainingEnergy <= 0f;
    }

    /** Spends `damageAbsorbed` of stored energy and one charge. Used for both energy-covered hits and stamina-covered ones - staminaBound wards call this with 0 once their own energy is gone. */
    public ActiveWard afterAbsorbing(float damageAbsorbed) {
        return new ActiveWard(id, casterId, type,
            Math.max(0f, remainingEnergy - damageAbsorbed), maxEnergy,
            maxCharges, chargesUsed + 1, visibleToOthers, staminaBound);
    }
}
