package com.dragonspeech.weapon;

import net.minecraft.world.item.Item;

/** Marker item whose client model is rendered entirely from Dragon Speech holographic geometry. */
public final class ConjuredWeaponItem extends Item {
    private final ToolType toolType;

    public ConjuredWeaponItem(ToolType toolType) {
        super(new Item.Properties().stacksTo(1));
        this.toolType = toolType;
    }

    public ToolType toolType() {
        return toolType;
    }
}
