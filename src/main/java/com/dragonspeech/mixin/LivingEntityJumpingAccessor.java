package com.dragonspeech.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * FIX: "jumping has protected access in LivingEntity" - real crash log
 * error. Dragon Mounts Legacy's own driver.jumping check works
 * unmodified on NeoForge because NeoForge's access transformers widen
 * this field to public automatically; Fabric has no equivalent.
 *
 * A COMMON mixin (this package, dragonspeech.mixins.json), not a
 * client-only one - the jumping check happens in server-authoritative
 * riding logic (getRiddenInput/tickRidden), which must work on a
 * dedicated server with zero client code present at all, not just in
 * singleplayer's integrated server.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityJumpingAccessor {
    @Accessor("jumping")
    boolean dragonspeech$isJumping();
}
