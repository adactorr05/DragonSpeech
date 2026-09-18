package com.dragonspeech.weapon;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The fixed, compiled set of materials a thrown-weapon noun word (jarn,
 * gull, demantr, ...) can select - same hard-capped-enum pattern as
 * BlockType/SummonType/WoundType elsewhere in this project. A datapack
 * word TAGS itself with one of these via its tool_material field, but the
 * actual damage/behavior each tier grants is fixed here in code and can
 * never be invented by combining words.
 *
 * If no material noun is spoken alongside a tool/weapon noun, the hurl
 * defaults to WOOD (the weakest tier) - see HurlWeaponEffectHandler.
 */
public enum ToolMaterial implements StringRepresentable {
    WOOD,
    GOLD,
    STONE,
    IRON,
    DIAMOND,
    NETHERITE;

    public static final Codec<ToolMaterial> CODEC = StringRepresentable.fromEnum(ToolMaterial::values);

    /**
     * How hard this material hits, relative to the tool's own base
     * damage. Ordered the same way vanilla tool tiers imply strength
     * (wood/gold weakest, netherite strongest).
     *
     * COPPER was deliberately left out of this tier list: 1.21.1 has no
     * vanilla copper tools to anchor a pickup item against (see the note
     * that used to live in WeaponItems). Add it back - as its own tier
     * here, plus a real vanilla-or-custom pickup item in WeaponItems -
     * if/when this project ports to a Minecraft version with real copper
     * tools.
     */
    public float damageMultiplier() {
        return switch (this) {
            case WOOD -> 0.70f;
            case GOLD -> 0.75f;
            case STONE -> 0.85f;
            case IRON -> 1.00f;
            case DIAMOND -> 1.30f;
            case NETHERITE -> 1.50f;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
