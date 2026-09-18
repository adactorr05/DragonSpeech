package com.dragonspeech.item;

import com.dragonspeech.network.DragonSpeechNetworking;
import com.dragonspeech.storage.DragonSpeechComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * A tablet of the old tongue, in three tiers of age and difficulty.
 * Studying it (right-click) opens the reading screen: a passage of
 * damaged pseudo-language with 1-3 real words hidden in it. Content is
 * generated ONCE on first study and stored on the stack - a tablet's
 * text never changes, so it can be traded, and two people studying the
 * same tablet see the same inscription.
 *
 * Learning happens through the translation minigame (see TabletScreen /
 * TranslateAttemptHandler), never as a free grant.
 */
public class WordTabletItem extends Item {

    private static final Random RANDOM = new Random();

    private final int tier;

    public WordTabletItem(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        String content = stack.get(DragonSpeechComponents.TABLET_CONTENT);
        if (content == null) {
            content = TabletContentGenerator.generate(tier, RANDOM);
            stack.set(DragonSpeechComponents.TABLET_CONTENT, content);
        }

        DragonSpeechNetworking.sendOpenTablet(serverPlayer, content);
        return InteractionResultHolder.success(stack);
    }
}
