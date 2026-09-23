package com.dragonspeech.enchant;

import com.dragonspeech.ward.WardType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One Dragon Speech ward/blessing/curse entry stored on an item. */
public record MagicEnchantment(
    String wordId,
    EnchantmentKind kind,
    WardPowerSource powerSource,
    float durabilityCurrent,
    float durabilityMax,
    int level,
    boolean removable,
    String wardDamageType,
    boolean hidden,
    long expiresAt,
    String boundCasterId
) {
    public static final Codec<MagicEnchantment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("word_id").forGetter(MagicEnchantment::wordId),
        Codec.STRING.xmap(EnchantmentKind::valueOf, Enum::name).fieldOf("kind").forGetter(MagicEnchantment::kind),
        Codec.STRING.xmap(WardPowerSource::valueOf, Enum::name).fieldOf("power_source").forGetter(MagicEnchantment::powerSource),
        Codec.FLOAT.fieldOf("durability_current").forGetter(MagicEnchantment::durabilityCurrent),
        Codec.FLOAT.fieldOf("durability_max").forGetter(MagicEnchantment::durabilityMax),
        Codec.INT.fieldOf("level").forGetter(MagicEnchantment::level),
        Codec.BOOL.fieldOf("removable").forGetter(MagicEnchantment::removable),
        Codec.STRING.optionalFieldOf("ward_damage_type", "").forGetter(MagicEnchantment::wardDamageType),
        Codec.BOOL.optionalFieldOf("hidden", false).forGetter(MagicEnchantment::hidden),
        Codec.LONG.optionalFieldOf("expires_at", -1L).forGetter(MagicEnchantment::expiresAt),
        Codec.STRING.optionalFieldOf("bound_caster_id", "").forGetter(MagicEnchantment::boundCasterId)
    ).apply(instance, MagicEnchantment::new));

    public static MagicEnchantment newWard(String wordId, WardPowerSource source, float reserveMax, int level,
                                            long expiresAt, String casterId) {
        float stored = (source == WardPowerSource.RESERVE || source == WardPowerSource.DURABILITY) ? reserveMax : 0f;
        return new MagicEnchantment(wordId, EnchantmentKind.WARD, source, stored, reserveMax, level, true, "", false,
            expiresAt, casterId == null ? "" : casterId);
    }

    public static MagicEnchantment newTypedWard(String wordId, WardPowerSource source, float reserveMax, int level,
                                                 WardType wardType, long expiresAt, String casterId) {
        float stored = (source == WardPowerSource.RESERVE || source == WardPowerSource.DURABILITY) ? reserveMax : 0f;
        return new MagicEnchantment(wordId, EnchantmentKind.WARD, source, stored, reserveMax, level, true, wardType.name(), false,
            expiresAt, casterId == null ? "" : casterId);
    }

    /** Legacy constructor used by old callers: legacy stored-power ward. */
    public static MagicEnchantment newWard(String wordId, WardPowerSource source, float durabilityMax, int level) {
        return newWard(wordId, source, durabilityMax, level, -1L, "");
    }
    public static MagicEnchantment newTypedWard(String wordId, WardPowerSource source, float durabilityMax, int level, WardType wardType) {
        return newTypedWard(wordId, source, durabilityMax, level, wardType, -1L, "");
    }

    public static MagicEnchantment newBlessing(String wordId, int level) {
        return new MagicEnchantment(wordId, EnchantmentKind.BLESSING, WardPowerSource.RESERVE, 0f, 0f, level, false, "", false, -1L, "");
    }

    public static MagicEnchantment newCurse(String wordId, int level) {
        return new MagicEnchantment(wordId, EnchantmentKind.CURSE, WardPowerSource.RESERVE, 0f, 0f, level, false, "", false, -1L, "");
    }

    public boolean isActive() {
        return kind != EnchantmentKind.WARD || powerSource == WardPowerSource.DURATION
            || powerSource == WardPowerSource.STAMINA_LINKED || durabilityCurrent > 0f;
    }

    public boolean isActive(long gameTime) {
        if (kind != EnchantmentKind.WARD) return true;
        if (powerSource == WardPowerSource.DURATION) return expiresAt < 0L || gameTime < expiresAt;
        if (powerSource == WardPowerSource.STAMINA_LINKED) return true;
        return durabilityCurrent > 0f;
    }

    public boolean wardMatches(WardType incoming) {
        return wardDamageType.isEmpty() || wardDamageType.equals(incoming.name());
    }

    public MagicEnchantment withDurability(float newCurrent) {
        return new MagicEnchantment(wordId, kind, powerSource,
            Math.max(0f, Math.min(durabilityMax, newCurrent)), durabilityMax, level, removable,
            wardDamageType, hidden, expiresAt, boundCasterId);
    }

    public MagicEnchantment withHidden(boolean newHidden) {
        return new MagicEnchantment(wordId, kind, powerSource, durabilityCurrent, durabilityMax, level,
            removable, wardDamageType, newHidden, expiresAt, boundCasterId);
    }
}
