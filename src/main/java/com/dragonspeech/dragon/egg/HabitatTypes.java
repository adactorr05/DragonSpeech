package com.dragonspeech.dragon.egg;

import com.dragonspeech.dragon.egg.habitats.*;

/**
 * Forces all 7 habitat types' static initializers to actually run
 * (each one calls Habitat.register(...) from a `static {}` block) -
 * Java only loads a class when something first references it, so
 * without this, a habitat type nothing else happens to touch yet could
 * still be genuinely unregistered by the time a breed JSON tries to
 * parse a habitat of that type, producing a false "unknown habitat
 * type" error purely from class-loading order, not any real problem
 * with the JSON. Call bootstrap() once, early, before anything parses
 * habitat data - same ordering requirement DragonBreedRegistry has
 * relative to this.
 */
public final class HabitatTypes {
    private HabitatTypes() {}

    public static void bootstrap() {
        // FIX: real bug, confirmed against the actual crash
        // ("Unknown habitat type: any_of") - referencing .TYPE did NOT
        // actually force class loading at all, because TYPE is
        // declared `public static final String TYPE = "...";`, which
        // makes it a compile-time CONSTANT under the JLS (a specific,
        // well-defined rule: JLS 12.4.1 - accessing a constant
        // variable does not trigger class initialization, because the
        // compiler inlines the literal value directly at the call
        // site instead of generating a real field read). This entire
        // method was a genuine no-op the whole time it existed.
        //
        // .CODEC is different - it's built via RecordCodecBuilder/
        // MapCodec method calls, a complex runtime expression that is
        // NOT a compile-time constant, so referencing it genuinely
        // does force the class to initialize (running its static
        // registration block) before this method returns.
        @SuppressWarnings("unused")
        Object[] force = {
                NearbyBlocksHabitat.CODEC,
                FluidHabitat.CODEC,
                HeightHabitat.CODEC,
                BiomeHabitat.CODEC,
                LightHabitat.CODEC,
                DragonBreathHabitat.CODEC,
                PickyHabitat.CODEC,
                RainingHabitat.CODEC,
                NearbyLightningHabitat.CODEC,
                DimensionHabitat.CODEC,
                AnyOfHabitat.CODEC,
        };
    }
}