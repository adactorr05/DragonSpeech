package com.dragonspeech.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A synthetic public bridge to AbstractContainerMenu's addSlot(Slot) -
 * addSlot is protected (same situation this project's own
 * PlayerRendererShoulderMixin already ran into and documented for
 * addLayer: a plain cast-and-call from an @Inject method fails to
 * compile against a protected target member, even though the mixin is
 * bytecode-woven into the target class at runtime). @Invoker is Mixin's
 * own supported mechanism for exactly this - it generates a real
 * accessor method that bypasses normal Java visibility checking,
 * instead of trying to call the protected method directly the way that
 * first PlayerRenderer attempt did.
 *
 * This is a SEPARATE mixin (an interface, not the @Inject class that
 * uses it) because @Invoker methods must be declared on an interface
 * mixin targeting the class that OWNS the method - AbstractContainerMenu
 * here, not InventoryMenu, since addSlot is inherited, not redeclared.
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {

    @Invoker("addSlot")
    Slot dragonspeech$callAddSlot(Slot slot);
}
