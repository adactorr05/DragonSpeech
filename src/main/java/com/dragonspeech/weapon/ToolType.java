package com.dragonspeech.weapon;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The fixed, compiled set of tool/weapon shapes a thrown-weapon noun word
 * (sverd, oxi, haki, ...) can select - same hard-capped-enum pattern as
 * ToolMaterial. A word tags itself with one of these via tool_type; the
 * actual base damage each shape swings for is fixed here.
 *
 * Base numbers are deliberately NOT a copy of vanilla melee attack
 * damage (axes hit harder than swords in melee, but swords are faster
 * and more precise) - these are tuned for a THROWN, embedding weapon
 * instead: swords are the balanced baseline, axes are the heaviest
 * single hit, tridents sit close behind (their vanilla precedent is
 * already a thrown weapon), and the utility tools (pick/shovel/hoe) are
 * a deliberately weaker "you technically CAN throw a hoe at someone"
 * tier - flavorful, not a trap option, but never the best pick.
 */
public enum ToolType implements StringRepresentable {
    SWORD,
    AXE,
    PICKAXE,
    SHOVEL,
    HOE,
    SPEAR,
    TRIDENT;

    public static final Codec<ToolType> CODEC = StringRepresentable.fromEnum(ToolType::values);

    public float baseDamage() {
        return switch (this) {
            case SWORD -> 7.0f;
            case AXE -> 9.0f;
            case PICKAXE -> 6.0f;
            case SHOVEL -> 4.5f;
            case HOE -> 3.0f;
            case SPEAR -> 8.0f;
            case TRIDENT -> 8.5f;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
