package com.dragonspeech.client.mixin;

import com.dragonspeech.client.mind.PossessionClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While possessing (hambinda), the borrowed mob is pinned to the
 * player's exact position - which in first person means its model sits
 * inside the camera. This skips rendering ONLY that one entity, ONLY for
 * the possessing player, ONLY in first person: everyone else still sees
 * the mob (that's the whole presentation), and switching to third person
 * shows you the skin you're wearing, which is honestly the best part.
 *
 * VERSION-RISK NOTE: targets EntityRenderDispatcher#shouldRender - a
 * stable, frequently-mixin'd method (signature (Entity, Frustum, double,
 * double, double) -> boolean in 1.21.1 Mojang mappings). If the injection
 * ever fails to apply after an update, the equivalent fallback is a HEAD
 * inject on the render(...) method with ci.cancel() under the same
 * condition.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class PossessedMobRenderMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void dragonspeech$hidePossessedSkinInFirstPerson(Entity entity, Frustum frustum,
            double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (PossessionClientState.isPossessedMob(entity.getId())
            && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            cir.setReturnValue(false);
        }
    }
}
