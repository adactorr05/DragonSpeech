package com.dragonspeech.dragon;

import com.dragonspeech.engine.Element;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * "The reusable colored dragons should also have variations. Like the
 * Ender dragon has the purple breath, Red & orange variation dragons =
 * Fire, Blue variations = Water or lightning, ect." - all tameable/wild
 * colored dragons share ONE model and ONE base texture (reusing the
 * Ender Dragon's silhouette per the design notes), and DragonColor is
 * the entire difference between them: a tint applied at render time
 * (see the client-side renderer, not yet built) plus which Element their
 * breath and claws carry.
 *
 * Deliberately reuses com.dragonspeech.engine.Element rather than
 * inventing dragon-specific damage types - a Red dragon's breath is
 * mechanically the exact same FIRE impact a fire-domain spell would
 * apply (see Element#hitEntity for the player-cast version; dragons use
 * their own lighter-weight application in DragonBreathAttackGoal since
 * Element#hitEntity requires a ServerPlayer caster and a dragon has
 * none), which also means a fire ward (eldverja) should sensibly reduce
 * a Red dragon's breath too if the ward system is ever wired up to mob
 * damage sources generally.
 */
public enum DragonColor implements StringRepresentable {

    RED(Element.FIRE, 0xb32418),
    BRONZE(Element.FIRE, 0xa8742a),
    BLUE(Element.LIGHTNING, 0x2a5fa8),
    WHITE(Element.ICE, 0xd8ecf5),
    GREEN(Element.POISON, 0x2f8f3a),
    BLACK(Element.DEATH, 0x1c1622),
    /** The Ender Dragon herself, reworked - purple breath per the design notes, kept distinct from the tameable palette above. */
    ENDER(Element.SHADOW, 0x7c2fb8);

    public static final Codec<DragonColor> CODEC = StringRepresentable.fromEnum(DragonColor::values);

    private final Element breathElement;
    private final int tint;

    DragonColor(Element breathElement, int tint) {
        this.breathElement = breathElement;
        this.tint = tint;
    }

    public Element breathElement() {
        return breathElement;
    }

    /** RGB tint the shared dragon texture is multiplied by at render time. */
    public int tint() {
        return tint;
    }

    // customModelData() removed - it drove the old single-item texture-
    // override system, now replaced entirely by 7 separate block-items
    // (see DragonSpeechBlocks) that each have their own real block/item
    // model. No component-driven texture switching needed anymore -
    // which block-item you have IS the texture.

    private static final DragonColor[] TAMEABLE_POOL = {RED, BRONZE, BLUE, WHITE, GREEN, BLACK};

    /** ENDER is not part of the random-hatch/wild-spawn pool - she is unique, per "The ender dragon should be considered a 'wild' Dragon." Used for live wild-spawn rolls (DragonEntity#finalizeSpawn), which only have the entity's own RandomSource available. */
    public static DragonColor randomTameablePalette(net.minecraft.util.RandomSource random) {
        return TAMEABLE_POOL[random.nextInt(TAMEABLE_POOL.length)];
    }

    /** Same pool, for callers working from a plain seeded java.util.Random instead of an entity's RandomSource - see DragonEggItem, which needs a deterministic, egg-specific seed rather than the world's RNG. */
    public static DragonColor randomTameablePalette(java.util.Random random) {
        return TAMEABLE_POOL[random.nextInt(TAMEABLE_POOL.length)];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
