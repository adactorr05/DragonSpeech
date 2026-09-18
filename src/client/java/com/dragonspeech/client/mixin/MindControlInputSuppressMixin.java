package com.dragonspeech.client.mixin;

import com.dragonspeech.client.mind.MindControlClientState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses vanilla's own left-click "attack" and right-click "use"
 * handling while Control is active - this is a real, load-bearing fix,
 * not polish.
 *
 * MindControlClientState only ever POLLS raw key state to build its own
 * custom input payload; it never consumes or cancels the underlying
 * click. That means vanilla's normal attack/use pipeline keeps firing
 * too, using Minecraft's own hitResult (which, since the camera has been
 * redirected to the controlled target via setCamera(), is raycasting
 * from the TARGET's viewpoint) - but the resulting vanilla packet is
 * still attributed to the REAL player entity server-side, whose ACTUAL
 * position is wherever their body was left standing, often far from
 * whatever the camera is looking at. The server's own vanilla validation
 * then rejects the entity as out of the real player's reach
 * ("Attempting to attack an invalid entity") and disconnects them -
 * this is what was causing the crash-to-server-list.
 *
 * VERSION-RISK NOTE: startAttack() and startUseItem() do NOT share a
 * return type in this Minecraft version - startAttack() returns boolean
 * (hence CallbackInfoReturnable below) but startUseItem() returns void
 * (hence plain CallbackInfo with ci.cancel() instead of
 * cir.setReturnValue()). This asymmetry is exactly what broke the first
 * version of this mixin - if either injection ever fails to apply again
 * with an "Invalid descriptor... Expected (...)V but found (...)"
 * error, that's Mojang having changed one of these two signatures again;
 * check Minecraft's decompiled source for the current one and adjust
 * just that method's callback type/cancel call to match.
 */
@Mixin(Minecraft.class)
public abstract class MindControlInputSuppressMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void dragonspeech$suppressAttackWhileControlling(CallbackInfoReturnable<Boolean> cir) {
        if (MindControlClientState.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void dragonspeech$suppressUseWhileControlling(CallbackInfo ci) {
        if (MindControlClientState.isActive()) {
            ci.cancel();
        }
    }
}