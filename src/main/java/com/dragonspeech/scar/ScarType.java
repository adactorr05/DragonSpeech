package com.dragonspeech.scar;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What kind of lasting harm a crossing leaves. WORD_LOSS is the common
 * case; everything past it is a genuine, permanent handicap in the
 * spirit of the source material - Eragon's chronic back pain, Oromis's
 * inability to work costly magic after his own scarring.
 */
public enum ScarType implements StringRepresentable {
    WORD_LOSS,        // 1-3 known words severed - see ActiveScar.lostWords
    CHRONIC_PAIN,     // periodic unbidden pain - occasional Slowness + minor damage
    COST_CEILING,     // can no longer cast anything above a certain cost, ever
    REGEN_CAP,        // stamina will not regenerate past a fraction of max
    REGEN_DISABLED,   // stamina never regenerates on its own again - the severe end
    CURSED_WORD;      // one specific known word now hurts the speaker every time it's spoken

    public static final Codec<ScarType> CODEC = StringRepresentable.fromEnum(ScarType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
