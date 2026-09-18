package com.dragonspeech.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws simple background squares for the 4 accessory slots the
 * InventoryMenu mixin adds - vanilla's own inventory.png has no slot
 * frame baked in at these new positions, so this draws plain highlighted
 * squares instead - GuiGraphics.fill, the same simple, already-proven-
 * safe primitive every other custom screen in this project already uses
 * successfully.
 *
 * FIXED (invisibility): this used to inject at HEAD of render() - which
 * draws BEFORE vanilla's own renderBg() blits the inventory.png panel
 * artwork, so that background blit was painting straight over the
 * squares every frame, right after they were drawn. Now injects at the
 * TAIL of renderBg() instead - the same hook point vanilla itself uses
 * to draw its OWN panel artwork, so this draws immediately after that
 * (visible, not painted over) and still before the generic slot/item
 * rendering pass that runs after renderBg() returns (so real equipped
 * items still draw on TOP of these backgrounds, not under them).
 *
 * REPOSITIONED TWICE - see InventoryMenuAccessoryMixin's own note for
 * the full history. Now drawn OUTSIDE vanilla's 176px-wide panel
 * entirely (x=184/202, past that right edge), with a small attached
 * extension panel background drawn behind them so the whole thing reads
 * as one intentional addition rather than 4 floating squares.
 *
 * VERSION-RISK NOTE: renderBg's parameter order is (GuiGraphics, float
 * partialTick, int mouseX, int mouseY) - partialTick comes SECOND here,
 * unlike render()'s own (GuiGraphics, int, int, float) order. That's
 * vanilla's own long-standing convention difference between the two
 * methods. The SAME x/y arrays as InventoryMenuAccessoryMixin - keep
 * them in sync if you move the slots.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenAccessoryMixin {

    private static final int[] SLOT_XS = {184, 202, 184, 202};
    private static final int[] SLOT_YS = {8, 8, 26, 26};

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void dragonspeech$drawAccessorySlotBackgrounds(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        int leftPos = accessor.dragonspeech$getLeftPos();
        int topPos = accessor.dragonspeech$getTopPos();

        // The attached extension panel behind the 4 slots - drawn once,
        // roughly bracketing the 2x2 block with a small margin, so it
        // reads as "an added-on pouch" rather than disconnected squares.
        guiGraphics.fill(leftPos + 178, topPos + 2, leftPos + 226, topPos + 48, 0xFFC6C6C6); // light outer frame, matching vanilla's own panel-edge tone
        guiGraphics.fill(leftPos + 180, topPos + 4, leftPos + 224, topPos + 46, 0xFF8B8B8B); // inset body

        for (int i = 0; i < SLOT_XS.length; i++) {
            int x = leftPos + SLOT_XS[i];
            int y = topPos + SLOT_YS[i];
            guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B); // light frame
            guiGraphics.fill(x, y, x + 16, y + 16, 0xFF373737); // dark slot interior, matches vanilla's own empty-slot shading closely enough without needing its exact texture
        }
    }
}
