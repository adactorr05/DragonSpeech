package com.dragonspeech.dragon;

import com.dragonspeech.growth.AttunementAccess;
import com.dragonspeech.entity.DragonSpeechEntities;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.word.Domain;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Random;

/**
 * "I want it to be 7 eggs. 1 egg for each color, and that color is
 * based off what dragon will hatch" per explicit direction - replaces
 * the old single generic DragonEggItem (now removed). Each instance is
 * tied to exactly one DragonColor, fixed at construction - color is no
 * longer a random roll on first use, it's just which of the 7 items you
 * have.
 *
 * "leave the bonding/hatching logic alone" per explicit direction -
 * checkEgg/isCompatible/attemptBond below are copied VERBATIM from the
 * old DragonEggItem, not one line changed. The ONLY thing that's
 * different is ensureSeeded's color line: it used to roll a random
 * DragonColor; now it just writes this item's own fixed color. Every
 * other mechanic - the never-bondable roll, per-player compatibility
 * seeded by egg+player, the sneak-to-check/sneak-to-bond split, the
 * dragon spawning and bonding itself - is untouched.
 *
 * Extends BlockItem rather than Item, since this is now also a
 * placeable block (see VariantDragonEggBlock) - use() here handles
 * right-clicking on AIR/nothing (the check/bond interaction, exactly as
 * before); BlockItem's own inherited useOn() is untouched and still
 * handles right-clicking a surface to place the block. These are
 * separate vanilla interaction paths that don't conflict - the practical
 * effect is that checking/bonding needs you to not be looking at a
 * placeable surface within reach (open air/sky), otherwise a right-click
 * will place the egg instead. Worth knowing if it ever feels like
 * checking "isn't working" - it's very likely placing the block instead.
 */
public class DragonEggBlockItem extends BlockItem {

    private static final float NEVER_BONDABLE_CHANCE = 0.08f;
    private static final float BASE_COMPATIBILITY_THRESHOLD = 0.45f;
    private static final float COMPATIBILITY_PER_ATTUNEMENT = 0.003f; // +0.3% per point, capped below

    private final DragonColor color;

    public DragonEggBlockItem(Block block, Properties properties, DragonColor color) {
        super(block, properties);
        this.color = color;
    }

    public DragonColor color() {
        return color;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player playerEntity, InteractionHand hand) {
        ItemStack stack = playerEntity.getItemInHand(hand);
        if (level.isClientSide || !(playerEntity instanceof ServerPlayer player)) {
            return InteractionResultHolder.pass(stack);
        }

        ensureSeeded(stack);

        // FIX: "sneak+use = instantly spawn a bonded dragon"
        // (attemptBond()) is now dead/actively-wrong code - the whole
        // egg-item-triggers-bonding-directly flow is superseded by
        // placing the egg and letting VariantDragonEggBlock's own
        // hatch() handle spawning/bonding through the real
        // habitat-gated wait. Left uncaught, sneak+use here would
        // instantly spawn a bonded dragon while completely bypassing
        // the entire habitat/hatching system just built - a real
        // conflict, not a stale API call. Both sneak and non-sneak
        // use-in-air now just check compatibility (checkEgg()) - the
        // only thing left for the ITEM itself to do; actual hatching
        // only happens once placed.
        checkEgg(player, stack);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private void ensureSeeded(ItemStack stack) {
        if (!stack.has(DragonSpeechComponents.EGG_SEED)) {
            long seed = new Random().nextLong();
            stack.set(DragonSpeechComponents.EGG_SEED, seed);
            stack.set(DragonSpeechComponents.EGG_NEVER_BONDABLE, EggCompatibility.rollNeverBondable(seed));
            // Only line different from the old DragonEggItem - color is
            // this item's own fixed identity now, not a random roll.
            stack.set(DragonSpeechComponents.EGG_COLOR, color.getSerializedName());
        }
    }

    // --- Everything below is copied verbatim from the old DragonEggItem - see this class's own doc. ---

    private void checkEgg(ServerPlayer player, ItemStack stack) {
        if (Boolean.TRUE.equals(stack.get(DragonSpeechComponents.EGG_NEVER_BONDABLE))) {
            player.sendSystemMessage(Component.literal("The egg is cold and still. Whatever sleeps inside will not wake for anyone. This one cannot be bonded."));
            return;
        }
        if (isCompatible(player, stack)) {
            player.sendSystemMessage(Component.literal(
                    "Something stirs faintly as you touch the shell - it could bond to you. "
                            + "Place it somewhere fitting and right-click it to begin - it will hatch in its own time."));
        } else {
            player.sendSystemMessage(Component.literal(
                    "The egg is dormant under your touch, but nothing answers. You cannot bond with this one."));
        }
    }

    private boolean isCompatible(ServerPlayer player, ItemStack stack) {
        Long seedBoxed = stack.get(DragonSpeechComponents.EGG_SEED);
        long seed = seedBoxed != null ? seedBoxed : 0L;
        return EggCompatibility.isCompatible(player, seed);
    }
}
