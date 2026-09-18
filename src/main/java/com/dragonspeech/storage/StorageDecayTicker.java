package com.dragonspeech.storage;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Runs on an interval rather than every tick - both for performance (no
 * reason to scan every player's whole inventory 20 times a second) and
 * because per-tick decay amounts would be too small to represent cleanly
 * as a float without drifting.
 */
public final class StorageDecayTicker {

    private static final int DECAY_INTERVAL_TICKS = 200; // every 10 seconds
    private static int counter = 0;

    private StorageDecayTicker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            counter++;
            if (counter < DECAY_INTERVAL_TICKS) {
                return;
            }
            counter = 0;
            applyDecay(server);
        });
    }

    private static void applyDecay(MinecraftServer server) {
        float minutesPerInterval = DECAY_INTERVAL_TICKS / 20f / 60f;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Curse of the Withering Grasp ("visnatak") - triples decay
            // (its own single level, per spec) on EVERYTHING ELSE the
            // wearer carries, inventory and accessory slots alike.
            float witheringMultiplier = com.dragonspeech.enchant.EquippedEnchantments.has(player, "visnatak") ? 3f : 1f;
            float decayAmountPerMinute = minutesPerInterval * witheringMultiplier;

            for (ItemStack stack : player.getInventory().items) {
                decayOne(stack, decayAmountPerMinute);
            }

            java.util.List<ItemStack> accessories = com.dragonspeech.accessory.AccessorySlotsAccess.get(player);
            boolean accessoriesChanged = false;
            for (ItemStack stack : accessories) {
                if (decayOne(stack, decayAmountPerMinute)) {
                    accessoriesChanged = true;
                }
            }
            if (accessoriesChanged) {
                com.dragonspeech.accessory.AccessorySlotsAccess.set(player, accessories);
            }
        }
    }

    /** @return true if this stack actually had any stored energy to decay (used by the accessory-slot loop to know whether to write the list back at all). */
    private static boolean decayOne(ItemStack stack, float minutesPerInterval) {
        if (stack.isEmpty()) {
            return false;
        }
        return StorageMediumRegistry.propertiesOf(stack.getItem()).map(props -> {
            if (props.decayPerMinute() <= 0f) {
                return false;
            }
            float stored = ItemStaminaStorage.storedIn(stack);
            if (stored <= 0f) {
                return false;
            }
            float decayThisInterval = props.decayPerMinute() * minutesPerInterval;
            float newValue = Math.max(0f, stored - decayThisInterval);
            stack.set(DragonSpeechComponents.STORED_STAMINA, newValue);
            return true;
        }).orElse(false);
    }
}
