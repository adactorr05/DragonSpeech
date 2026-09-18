package com.dragonspeech.mixin;

import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * COMPILE FIX: salt() is genuinely real (confirmed against the actual
 * decompiled source), but it's declared protected on StructurePlacement,
 * the superclass - not public. Calling it through an explicit external
 * cast in RandomSpreadStructurePlacementMixin hit normal Java protected-
 * access rules (a mixin class doesn't actually extend the target at the
 * source level, so the compiler treats a cast-then-call as an external
 * reference, not inherited access).
 *
 * This is the standard, low-risk Mixin pattern for exactly this
 * situation - a tiny accessor interface exposing one protected member,
 * rather than trying to make the main mixin class itself extend
 * StructurePlacement (which would also need to satisfy that class's own
 * multi-argument constructor, a much messier path).
 */
@Mixin(StructurePlacement.class)
public interface StructurePlacementSaltAccessor {
    @Invoker("salt")
    int dragonspeech$salt();
}