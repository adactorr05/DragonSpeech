package com.dragonspeech.mixin;

import com.dragonspeech.accessory.AccessoryContainer;
import com.dragonspeech.accessory.AccessorySlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the 4 accessory slots directly into the vanilla player inventory
 * menu, rather than a separate screen - real Slot objects registered
 * here get vanilla's own click/drag/shift-click/network-sync handling
 * for free (see AccessoryContainer's own doc for why that matters).
 * Actually adding the slot goes through AbstractContainerMenuAccessor's
 * @Invoker bridge, not a direct call - see that class's doc for why a
 * direct call would repeat a compile failure this project already hit
 * once before (PlayerRendererShoulderMixin/addLayer).
 *
 * VERSION-RISK NOTE (UPDATE: constructor signature now CONFIRMED against
 * a real build - see below): every other mixin here
 * (LivingEntityDamageMixin, PlayerRendererShoulderMixin) was written
 * against decompiled source you provided directly. InventoryMenu
 * originally was NOT - the first attempt guessed
 * `(Inventory, boolean, LivingEntity)` for the constructor, which was
 * close but not quite right: the real third parameter is `Player`, not
 * the more general `LivingEntity` (Mixin's own injection-apply error
 * reported the exact expected descriptor, which is how this got fixed
 * without needing decompiled source after all). Confirmed working
 * signature: `(Inventory, boolean, Player)`.
 *
 * SERVER VS CLIENT CONTAINER: the SERVER's own InventoryMenu instance
 * (owner is a real ServerPlayer) gets a real, attachment-backed
 * AccessoryContainer - that's the authoritative copy. Every OTHER
 * construction (the CLIENT's own local mirror of that same menu, or any
 * non-player owner) gets a plain in-memory SimpleContainer instead - it
 * never needs to persist anything itself, since vanilla's own Slot
 * synchronization keeps it mirroring whatever the server's real
 * container contains.
 *
 * SLOT POSITIONS: REPOSITIONED TWICE now after both previous placements
 * turned out to overlap real vanilla content (x=148 sat on the crafting
 * output slot; the "above offhand" x=77/95 attempt still clipped the
 * crafting input grid). Rather than guess at a third set of coordinates
 * squeezed into vanilla's own cramped internal margins - which is
 * exactly what went wrong twice - these now sit OUTSIDE the vanilla
 * panel's footprint entirely: vanilla's inventory panel is a
 * long-standing, stable 176px wide, so x=184/202 (just past that right
 * edge, with a small gap) is GUARANTEED clear of every vanilla element
 * regardless of exactly where they sit internally. The matching
 * InventoryScreen mixin draws a small attached extension panel behind
 * them so they read as an intentional addition, not 4 floating squares.
 * Still can't be fully confirmed without seeing it render, but this
 * approach can no longer overlap anything vanilla the way the first two
 * attempts did - if it's still wrong, it'll be about how it LOOKS
 * (position of the extension panel itself), not overlap. The matching
 * InventoryScreen mixin (drawing the slot-background frames) uses the
 * SAME two arrays, so keep them in sync if you change one.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuAccessoryMixin {

    @Unique
    private Container dragonspeech$accessoryContainer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void dragonspeech$addAccessorySlots(Inventory inventory, boolean isLocalPlayer, Player owner, CallbackInfo ci) {
        this.dragonspeech$accessoryContainer = (owner instanceof ServerPlayer serverPlayer)
            ? new AccessoryContainer(serverPlayer)
            : new SimpleContainer(AccessoryContainer.SLOT_COUNT);

        AbstractContainerMenuAccessor self = (AbstractContainerMenuAccessor) (Object) this;
        int[] xs = {184, 202, 184, 202};
        int[] ys = {8, 8, 26, 26};
        for (int i = 0; i < AccessoryContainer.SLOT_COUNT; i++) {
            self.dragonspeech$callAddSlot(new AccessorySlot(this.dragonspeech$accessoryContainer, i, xs[i], ys[i]));
        }
    }
}
