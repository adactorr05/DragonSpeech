package com.dragonspeech.storage;

import com.dragonspeech.DragonSpeech;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Registers a custom data component holding how much stamina is stored
 * in an item stack. This is Minecraft's modern (1.20.5+) item-data
 * system, replacing the old raw-NBT-tag approach.
 *
 * VERSION-RISK NOTE: this is genuinely the newest API surface touched by
 * the whole project. DataComponentType.Builder's exact method names
 * (persistent(), networkSynchronizer()) have shifted before and may
 * differ slightly in your build. If this doesn't compile, check
 * DataComponentType.Builder in your decompiled sources for the current
 * method names - the overall shape (build a codec-backed component type,
 * register it to BuiltInRegistries.DATA_COMPONENT_TYPE) is correct even
 * if an individual method name has moved.
 */
public final class DragonSpeechComponents {

    public static final DataComponentType<Float> STORED_STAMINA = register(
            "stored_stamina",
            DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build()
    );

    /** A tablet's fixed, generated passage (JSON): tokens, embedded word ids, decoy meanings, and which words have been solved. Generated once on first study; stable and tradeable thereafter. */
    public static final DataComponentType<String> TABLET_CONTENT = register(
            "tablet_content",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /** The single word id a Scholar's Fragment reveals - assigned on first reading, permanent thereafter, making each fragment a collectible dictionary piece. */
    public static final DataComponentType<String> FRAGMENT_WORD = register(
            "fragment_word",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /** The word id a Word Scroll (see com.dragonspeech.item.WordScrollItem) was bought to teach - fixed at trade-offer generation time, unlike FRAGMENT_WORD's lazy first-use roll. See com.dragonspeech.mob.casting.MobTradeOffers. */
    public static final DataComponentType<String> TAUGHT_WORD = register(
            "taught_word",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /**
     * RESTORED - lost when a stale copy of this file overwrote the
     * accumulated working version during the Dragon Heart rename pass.
     * Recovered from an earlier round's own actual delivered source
     * (round 6), not reconstructed from memory - same field, same
     * comment, verbatim.
     *
     * The domain name shown on an unidentified Word Scroll's tooltip
     * ("Domain: Fire") - see WordScrollItem.appendHoverText and
     * MobTradeOffers.buildOffer. Deliberately NOT the word itself - the
     * true name stays hidden until the scroll is actually read.
     */
    public static final DataComponentType<String> WORD_CATEGORY_HINT = register(
            "word_category_hint",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    // --- Dragon egg bonding (see com.dragonspeech.dragon.DragonEggItem) ---

    /** A per-egg random seed, assigned once on the egg's very first check so bondability rolls are stable/reproducible for that specific egg rather than re-rolling on every interaction. */
    public static final DataComponentType<Long> EGG_SEED = register(
            "egg_seed",
            DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build()
    );

    /** True if this specific egg rolled "not even possible to bond with" at creation - permanent, applies to every player. */
    public static final DataComponentType<Boolean> EGG_NEVER_BONDABLE = register(
            "egg_never_bondable",
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    /** Which DragonColor this egg will hatch as - assigned once on first check, permanent thereafter. */
    public static final DataComponentType<String> EGG_COLOR = register(
            "egg_color",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    // --- Dragon Heart (see com.dragonspeech.eldunari) ---

    /** UNBONDED / PERMITTED / BROKEN / CONTESTED - see DragonHeartState. */
    public static final DataComponentType<String> ELDUNARI_STATE = register(
            "eldunari_state",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /** How much of the bonded dragon's stored lifetime energy remains in this heart - drained by DragonHeartAccess, distinct from the ordinary gold/emerald/diamond/netherite storage-gem pool. */
    public static final DataComponentType<Float> ELDUNARI_ENERGY = register(
            "eldunari_energy",
            DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build()
    );

    /** Stable identity for a specific Dragon Heart item, assigned once at creation - used only to re-find the exact stack in a player's inventory after a mind duel resolves (see DragonHeartService), since ItemStacks aren't otherwise addressable by UUID. */
    public static final DataComponentType<java.util.UUID> ELDUNARI_ID = register(
            "eldunari_id",
            DataComponentType.<java.util.UUID>builder()
                    .persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                    .build()
    );

    /** Flavor only - the name of the dragon this heart came from, if known. Empty string if unknown/never revealed. */
    public static final DataComponentType<String> ELDUNARI_SOURCE_NAME = register(
            "eldunari_source_name",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /**
     * "Once you get there, it should never open the mind duel with that
     * dragon heart again unless its a different person trying to access
     * it" per explicit direction. ELDUNARI_STATE (PERMITTED/BROKEN) is
     * stack-level, not player-specific - without this, ANY player
     * holding an already-broken heart could use it freely, even one who
     * never won the duel or received it themselves. This tracks WHO
     * actually earned access, so DragonHeartItem.use() can tell "same
     * person, skip straight to the GUI" from "different person, this
     * needs its own duel" even though the raw state string alone can't.
     */
    public static final DataComponentType<java.util.UUID> HEART_AUTHORIZED_PLAYER = register(
            "heart_authorized_player",
            DataComponentType.<java.util.UUID>builder()
                    .persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                    .build()
    );

    /**
     * "Dragon hearts should contain the stamina that the dragons age
     * was when it was removed... a hatchling will have lower max
     * stamina than an elder dragon" per explicit direction. Previously
     * max energy was just a flat constant (BROKEN_ENERGY/
     * MAD_BROKEN_ENERGY in DragonHeartService) regardless of which
     * dragon a heart came from or how old it was - this stores the
     * REAL, per-item cap instead, snapshotted once at the moment a
     * living dragon's heart is actually given (see DragonHeartService.
     * giveOwnHeart) from that dragon's own real maxDragonStamina() at
     * that exact moment - locked in permanently from then on, even as
     * the dragon itself (if still alive) continues aging and growing
     * past that point.
     *
     * Hearts that come from combat (a found/looted heart, no living
     * source dragon to reference) still use the flat constants - there's
     * no age data to lock in for those, since there was never a specific
     * living dragon involved in the first place.
     */
    public static final DataComponentType<Float> HEART_MAX_ENERGY = register(
            "heart_max_energy",
            DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build()
    );

    /**
     * "Setting for Using stamina, Setting for using before your stamina,
     * Setting for using after your stamina... should have similar
     * settings as the bonded dragon gui" per explicit direction -
     * mirrors DragonEntity's own useDragonStamina()/staminaBeforeOwn()
     * exactly (two independent booleans, not a 3-way enum), for
     * consistency with the pattern already established there. Default
     * true/true (on, before) when absent - a freshly-broken/permitted
     * heart should behave usefully out of the box, matching how a
     * bonded dragon's own assist already defaults to "on."
     */
    public static final DataComponentType<Boolean> HEART_USE_STAMINA = register(
            "heart_use_stamina",
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    /** true = drawn before the player's own stamina (Tier -0.5, same slot the old unconditional behavior used), false = only after the player's own stamina is exhausted (new Tier 1.5-ish, mirroring BondedDragonAssist's own after-tier). */
    public static final DataComponentType<Boolean> HEART_STAMINA_BEFORE_OWN = register(
            "heart_stamina_before_own",
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );

    // --- Temporary conjured items (see com.dragonspeech.weapon.ConjuredWeaponItems) ---

    /** Absolute world game-time tick when a weapon created from nothing unravels. Presence of this component is also the authoritative "this is a temporary conjured item" marker. */
    public static final DataComponentType<Long> CONJURED_EXPIRES_AT = register(
            "conjured_expires_at",
            DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build()
    );

    /** Sustain rule for a conjured object: duration, reserve (afla), or caster (aflbinda). */
    public static final DataComponentType<String> CONJURED_SUSTAIN_MODE = register(
            "conjured_sustain_mode",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /** Caster UUID for aflbinda-bound constructs. Stored as text for simple codec/network compatibility. */
    public static final DataComponentType<String> CONJURED_OWNER = register(
            "conjured_owner",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /** Current/max independent spell reserve for constructs made with afla. */
    public static final DataComponentType<Float> CONJURED_RESERVE = register(
            "conjured_reserve",
            DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build()
    );
    public static final DataComponentType<Float> CONJURED_MAX_RESERVE = register(
            "conjured_max_reserve",
            DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build()
    );

    /** Word-of-Words can pause a duration-based construct's countdown without converting it to durability. */
    public static final DataComponentType<Boolean> CONJURED_DURATION_FROZEN = register(
            "conjured_duration_frozen",
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build()
    );
    public static final DataComponentType<Long> CONJURED_FROZEN_REMAINING = register(
            "conjured_frozen_remaining",
            DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build()
    );

    /** The magical substance the temporary weapon was created from (ice/fire/time/etc.), so its identity survives inventory storage and a later `taka` throw. */
    public static final DataComponentType<String> CONJURED_AFFINITY = register(
            "conjured_affinity",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /** Spoken physical/tool form of a temporary weapon (sword/axe/spear/etc.). Needed because some forms intentionally share the same vanilla backing item. */
    public static final DataComponentType<String> CONJURED_TOOL_TYPE = register(
            "conjured_tool_type",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    /** Spoken material tier of a temporary weapon. Kept separately from affinity so `jarn sverd` and `is sverd` retain the sentence that formed them. */
    public static final DataComponentType<String> CONJURED_MATERIAL = register(
            "conjured_material",
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build()
    );

    // --- Enchantment system (see com.dragonspeech.enchant) ---

    /** The list of ward/blessing/curse enchantments carried on this item - see MagicEnchantments/MagicEnchantment. Deliberately separate from vanilla's own real ItemEnchantments component (used for the vanilla-enchantment side of this system, Phase 2 - not built yet). */
    public static final DataComponentType<com.dragonspeech.enchant.MagicEnchantments> MAGIC_ENCHANTMENTS = register(
            "magic_enchantments",
            DataComponentType.<com.dragonspeech.enchant.MagicEnchantments>builder()
                    .persistent(com.dragonspeech.enchant.MagicEnchantments.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(com.dragonspeech.enchant.MagicEnchantments.CODEC))
                    .build()
    );

    private DragonSpeechComponents() {}

    private static <T> DataComponentType<T> register(String id, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, DragonSpeech.id(id), type);
    }

    /** Call from onInitialize() to force this class's static fields (and therefore registration) to run. */
    public static void bootstrap() {
        // Intentionally empty - referencing the class is enough to trigger the static initializer above.
    }
}