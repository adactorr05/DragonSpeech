package com.dragonspeech.storage;

import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * Shift+right-click a valid storage medium (while knowing the gather-
 * stamina word) to channel some of your OWN current stamina into it.
 * Stored energy belongs to the item afterward, not to you specifically -
 * see ItemStaminaStorage for why that matters.
 */
public final class ItemChargingHooks {

    private static final float CHARGE_PER_USE = 10f;

    private ItemChargingHooks() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);

            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!player.isShiftKeyDown() || !StorageMediumRegistry.isValidMedium(stack.getItem())) {
                return InteractionResultHolder.pass(stack);
            }

            if (!SkillsAccess.get(serverPlayer).gatherStamina()) {
                serverPlayer.sendSystemMessage(Component.literal("You do not know how to draw your strength into anything."));
                return InteractionResultHolder.fail(stack);
            }

            PlayerMagicData magic = StaminaAccess.get(serverPlayer);
            float toCharge = Math.min(CHARGE_PER_USE, magic.stamina());
            if (toCharge <= 0f) {
                serverPlayer.sendSystemMessage(Component.literal("You have no strength left to give."));
                return InteractionResultHolder.fail(stack);
            }

            float actuallyCharged = ItemStaminaStorage.charge(stack, toCharge);
            StaminaAccess.set(serverPlayer, magic.withStamina(magic.stamina() - actuallyCharged));

            serverPlayer.sendSystemMessage(Component.literal(
                actuallyCharged > 0f ? "Strength flows from you into the stone." : "It can hold no more."));

            return InteractionResultHolder.success(stack);
        });
    }
}
