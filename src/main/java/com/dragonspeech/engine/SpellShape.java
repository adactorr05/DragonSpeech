package com.dragonspeech.engine;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The fixed, compiled set of FORMS a working can take - the "shape" field
 * a form verb (kasta, geisla, kula, sprengja, hringr, skyja, umljomi,
 * ristmark, regnfalla) carries in its word JSON. Each shape maps to one
 * FormEngine in ElementalWorkingHandler; a datapack can add new words
 * that SELECT a shape, but never a new shape behavior.
 *
 * These are the reusable "effect engines" that replace Electroblob-style
 * one-off spells: EBW's Firebolt is FIRE+BOLT, Frost Ray is ICE+RAY,
 * Firestorm is FIRE+RAIN(+margfalt), Poison Cloud is POISON+CLOUD,
 * Healing Aura is LIFE+AURA, Fire Sigil is FIRE+SIGIL, and so on.
 */
public enum SpellShape implements StringRepresentable {
    /** A hurled strike along a line - the default projectile form (kasta). */
    BOLT,
    /** A straight piercing beam that touches everything along it (geisla). */
    RAY,
    /** A slow gathered sphere that bursts softly on arrival (kula). */
    ORB,
    /** An outward explosion at the point of impact (sprengja). */
    BURST,
    /** The working drawn in a circle around a centre (hringr). */
    RING,
    /** Scattered strikes falling from above (regnfalla). */
    RAIN,
    /** A lingering hanging mist (skyja). */
    CLOUD,
    /** A steady field around the caster (umljomi). */
    AURA,
    /** A mark placed on the ground that waits for a victim (ristmark). */
    SIGIL,
    /** A long, narrow piercing weapon-form (voddr). */
    LANCE,
    /** A flexible link between caster and mark (fjotbinda). */
    TETHER,
    /** Hooked hand-bound cutting forms (kral). */
    CLAW,
    /** A rotating/spiralling working around an axis or centre (sveira). */
    SPIRAL,
    /** A working wrapped closely around a body or mark (vefja). */
    SHELL;

    public static final Codec<SpellShape> CODEC = StringRepresentable.fromEnum(SpellShape::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
