package com.dragonspeech.enchant;

import com.dragonspeech.ward.WardType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One custom (ward/blessing/curse) enchantment living on an item -
 * DELIBERATELY NOT the same system as vanilla enchantments (Sharpness
 * etc; see ApplyVanillaEnchantEffectHandler and this class's own
 * earlier doc for why those two systems stay separate).
 *
 * `wordId` is the true_name of whichever specific word applied this.
 *
 * `powerSource`/`durabilityCurrent`/`durabilityMax` only mean anything
 * for kind=WARD.
 *
 * `wardDamageType` - ALSO only meaningful for kind=WARD. Empty string
 * means "generic, absorbs any wardable damage type" (galdrverja, the
 * original proof-of-concept word). A non-empty value holds a
 * WardType.name() (e.g. "FIRE") for the later damage-type-specific ward
 * words (eldgaldr, hoggaldr, sprengaldr, fallgaldr) - see
 * MagicWardCombat for where this actually gets filtered against
 * incoming damage.
 *
 * `removable` is true for wards by default and MUST be false forever
 * for any blessing/curse - baked in at creation time rather than
 * checked against `kind` everywhere removal is attempted, so Curse of
 * the Grave locking an item's OTHER enchantments permanently can do
 * that by flipping this flag, without special-case kind-checking at
 * every removal call site.
 *
 * `hidden` - Curse of Hiding's actual mechanism (see
 * ApplyHidingCurseEffectHandler). True on the TARGET entry being
 * concealed, never on the Hiding curse's own entry (that one always
 * shows in the tooltip, just without its level number - see
 * MagicEnchantmentTooltips). A hidden entry still fully exists and
 * still fully functions (a hidden ward still absorbs damage, a hidden
 * curse still curses) - hidden only ever means "not shown in the
 * tooltip," never "not active."
 */
public record MagicEnchantment(
    String wordId,
    EnchantmentKind kind,
    WardPowerSource powerSource,
    float durabilityCurrent,
    float durabilityMax,
    int level,
    boolean removable,
    String wardDamageType,
    boolean hidden
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
        Codec.BOOL.optionalFieldOf("hidden", false).forGetter(MagicEnchantment::hidden)
    ).apply(instance, MagicEnchantment::new));

    /** A fresh generic WARD entry (galdrverja) - freshly filled if durability-sourced. */
    public static MagicEnchantment newWard(String wordId, WardPowerSource powerSource, float durabilityMax, int level) {
        return new MagicEnchantment(wordId, EnchantmentKind.WARD, powerSource, durabilityMax, durabilityMax, level, true, "", false);
    }

    /** A fresh damage-type-specific WARD entry (eldgaldr etc). */
    public static MagicEnchantment newTypedWard(String wordId, WardPowerSource powerSource, float durabilityMax, int level, WardType wardType) {
        return new MagicEnchantment(wordId, EnchantmentKind.WARD, powerSource, durabilityMax, durabilityMax, level, true, wardType.name(), false);
    }

    public static MagicEnchantment newBlessing(String wordId, int level) {
        return new MagicEnchantment(wordId, EnchantmentKind.BLESSING, WardPowerSource.DURABILITY, 0f, 0f, level, false, "", false);
    }

    public static MagicEnchantment newCurse(String wordId, int level) {
        return new MagicEnchantment(wordId, EnchantmentKind.CURSE, WardPowerSource.DURABILITY, 0f, 0f, level, false, "", false);
    }

    public boolean isActive() {
        return kind != EnchantmentKind.WARD || powerSource == WardPowerSource.STAMINA_LINKED || durabilityCurrent > 0f;
    }

    /** True if this ward absorbs the given damage type - generic (empty wardDamageType) wards absorb anything wardable; typed ones only match their own type. */
    public boolean wardMatches(WardType incoming) {
        return wardDamageType.isEmpty() || wardDamageType.equals(incoming.name());
    }

    public MagicEnchantment withDurability(float newCurrent) {
        return new MagicEnchantment(wordId, kind, powerSource, Math.max(0f, Math.min(durabilityMax, newCurrent)), durabilityMax, level, removable, wardDamageType, hidden);
    }

    public MagicEnchantment withHidden(boolean newHidden) {
        return new MagicEnchantment(wordId, kind, powerSource, durabilityCurrent, durabilityMax, level, removable, wardDamageType, newHidden);
    }
}
