package com.dragonspeech.client.storage;

import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.storage.StorageMediumRegistry;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;

/**
 * Purely decorative - shows "Stored Stamina: X" on hover for a charged
 * storage medium. Safe to delete entirely if it doesn't compile against
 * your exact fabric-api version; nothing else in the project depends on
 * this file, and the core mechanic (draining/charging items) works
 * identically with or without it. This is deliberately the single
 * riskiest file this turn precisely because it's the least essential one.
 */
public final class StorageTooltipHooks {

    private StorageTooltipHooks() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!StorageMediumRegistry.isValidMedium(stack.getItem())) {
                return;
            }
            Float stored = stack.get(DragonSpeechComponents.STORED_STAMINA);
            if (stored != null && stored > 0f) {
                lines.add(Component.literal(String.format("Stored Stamina: %.1f", stored)));
            }
        });
    }
}
