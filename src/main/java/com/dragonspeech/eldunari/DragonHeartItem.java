package com.dragonspeech.eldunari;

import com.dragonspeech.dragon.DragonColor;
import com.dragonspeech.storage.DragonSpeechComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * "Dragon Heart" - replaces the single generic DragonHeartItem with one
 * real item per dragon color (7) plus the mad/dimmed variant, per
 * explicit direction ("Eldunari needs to be renamed to Dragon Heart...
 * there also need to be an eldunari for each color dragon... something
 * like a useless Dragon Heart"). Internal package/class names stay
 * "Eldunari" (DragonHeartService, DragonHeartState, DragonHeartAccess,
 * DragonHeartVesselEntity) - Java identifiers aren't player-facing, so
 * renaming that internal plumbing would be a large, purely-cosmetic risk
 * for no real benefit. Every PLAYER-FACING string (item name, tooltip,
 * chat messages) says "Dragon Heart" - that's what the copyright concern
 * is actually about.
 *
 * `color == null` means the mad variant - see isMad()/DragonHeartService's
 * own use of it for the harder duel and lower starting energy.
 *
 * The mind-duel state machine (checkEgg-equivalent, beginContact call,
 * tooltip) is copied from the old DragonHeartItem essentially unchanged -
 * see DragonHeartService/DragonHeartState for how BROKEN/PERMITTED are
 * reached, neither of which needed to change for the color split.
 *
 * NOTE: right-click while usable still just reports status via message
 * for now, same as the old item - the "rundown bonded-screen-style GUI
 * with before/after/off settings" is real, planned work, not yet built
 * in this pass. This item is written so that swapping the else-branch
 * to open that screen instead of sending a message is the only change
 * needed once it exists - nothing else here should need to change.
 */
public class DragonHeartItem extends Item {

    private final DragonColor color; // null = mad variant

    public DragonHeartItem(Properties properties, DragonColor color) {
        super(properties);
        this.color = color;
    }

    public DragonColor color() {
        return color;
    }

    public boolean isMad() {
        return color == null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player playerEntity, InteractionHand hand) {
        ItemStack stack = playerEntity.getItemInHand(hand);
        if (level.isClientSide || !(playerEntity instanceof net.minecraft.server.level.ServerPlayer player)) {
            return InteractionResultHolder.pass(stack);
        }

        String raw = stack.get(DragonSpeechComponents.ELDUNARI_STATE);
        DragonHeartState state;
        try {
            state = raw == null ? DragonHeartState.UNBONDED : DragonHeartState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            state = DragonHeartState.UNBONDED;
        }

        if (state == DragonHeartState.UNBONDED) {
            DragonHeartService.beginContact(player, stack);
        } else if (state == DragonHeartState.CONTESTED) {
            player.sendSystemMessage(Component.literal("Your mind is already pressed against this one."));
        } else {
            java.util.UUID authorized = stack.get(DragonSpeechComponents.HEART_AUTHORIZED_PLAYER);
            if (authorized == null) {
                // Backward compatibility: a heart that reached BROKEN/
                // PERMITTED before this check existed has no authorized
                // player recorded at all. Grandfather the current holder
                // in rather than suddenly demanding a duel for something
                // that already answered to them - and record it now so
                // this only ever happens once per such heart.
                stack.set(DragonSpeechComponents.HEART_AUTHORIZED_PLAYER, player.getUUID());
            } else if (!authorized.equals(player.getUUID())) {
                // "It should never open the mind duel with that dragon
                // heart again unless its a different person trying to
                // access it" per explicit direction - this IS that
                // different person. The stack's own state still says
                // usable, but that access was earned by someone else -
                // treat it exactly like UNBONDED for this player.
                player.sendSystemMessage(Component.literal("This heart does not know you. Its mind stirs, but does not yield."));
                DragonHeartService.beginContact(player, stack);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            DragonHeartService.openHeartScreen(player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String raw = stack.get(DragonSpeechComponents.ELDUNARI_STATE);
        DragonHeartState state;
        try {
            state = raw == null ? DragonHeartState.UNBONDED : DragonHeartState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            state = DragonHeartState.UNBONDED;
        }
        tooltip.add(switch (state) {
            case UNBONDED -> Component.literal(isMad()
                    ? "A crazed, half-mad mind thrashes within. Right-click to reach for it - if you dare."
                    : "A dormant mind sleeps within. Right-click to reach for it.").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE);
            case CONTESTED -> Component.literal("Contact in progress...").withStyle(net.minecraft.ChatFormatting.GRAY);
            case PERMITTED -> Component.literal("Bonded freely - answers to you.").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
            case BROKEN -> Component.literal("Its resistance is broken. It answers to you.").withStyle(net.minecraft.ChatFormatting.DARK_RED);
        });
        if (state.usable()) {
            Float energyBoxed = stack.get(DragonSpeechComponents.ELDUNARI_ENERGY);
            float energy = energyBoxed != null ? energyBoxed : 0f;
            float maxEnergy = DragonHeartService.resolveMaxEnergy(stack);
            tooltip.add(Component.literal("Stored strength: " + Math.round(energy) + " / " + Math.round(maxEnergy)).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (isMad()) {
            tooltip.add(Component.literal("This one is dimmer than the rest - mad, and much harder to reach.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
