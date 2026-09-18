package com.dragonspeech.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A synthetic public bridge to AbstractContainerScreen's protected
 * leftPos/topPos fields - these turned out to be declared on
 * AbstractContainerScreen (the shared base every container screen
 * extends), not on InventoryScreen itself, which is exactly why
 * InventoryScreenAccessoryMixin's original @Shadow attempt failed at
 * runtime ("@Shadow field leftPos was not located in the target class
 * ...InventoryScreen" - Mixin's @Shadow only looks at the exact target
 * class you declared, not its superclasses).
 *
 * Retargeting the WHOLE render-drawing mixin to AbstractContainerScreen
 * instead would have "fixed" this too, but would also have made it fire
 * for EVERY container screen in the game (crafting table, furnace,
 * chest...), not just the player's own inventory - the accessory slots
 * only make sense on the player inventory screen specifically. So
 * instead, same idea as AbstractContainerMenuAccessor's @Invoker bridge
 * for the protected addSlot method (which DID apply successfully - see
 * that class), this is a separate, narrowly-scoped accessor interface
 * just for reading these two fields across the class hierarchy, while
 * InventoryScreenAccessoryMixin itself stays targeted at InventoryScreen
 * only.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int dragonspeech$getLeftPos();

    @Accessor("topPos")
    int dragonspeech$getTopPos();
}
