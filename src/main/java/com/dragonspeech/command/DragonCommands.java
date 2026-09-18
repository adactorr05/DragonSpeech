package com.dragonspeech.command;

import com.dragonspeech.dragon.DragonEntity;
import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * The live Rider/dragon bond mechanics that don't belong to the egg
 * (bonding itself is an item interaction, see DragonEggItem) or the
 * Dragon Heart (its own item, see DragonHeartItem):
 *
 *   /dragon call [amount]   - "you can 'call' your dragon to boost or
 *                              'feed' you its own stamina to help you
 *                              with your spells" - draws from your
 *                              nearest bonded dragon's OWN internal
 *                              reserve into your stamina, provided it's
 *                              within reach.
 *   /dragon status           - show your bonded dragon(s)' stage/color/
 *                              stamina, if any are nearby.
 *
 * The old "/dragon eldunari" command (a repeatable stamina-charge grant
 * with no per-dragon limit at all) is REMOVED - replaced by the "Give
 * Heart" button on DragonBondScreen, a genuine one-time action gated on
 * DragonEntity.heartGiven() - see DragonHeartService.giveOwnHeart, the
 * button's actual handler now.
 */
public final class DragonCommands {

    private static final double CALL_RANGE = 32.0;
    private static final float DEFAULT_CALL_AMOUNT = 40f;

    private DragonCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("dragon")
                .then(Commands.literal("call")
                    .executes(context -> call(context.getSource(), DEFAULT_CALL_AMOUNT))
                    .then(Commands.argument("amount", FloatArgumentType.floatArg(1f, 500f))
                        .executes(context -> call(context.getSource(), FloatArgumentType.getFloat(context, "amount")))))
                .then(Commands.literal("status")
                    .executes(context -> status(context.getSource())))
                .then(Commands.literal("age")
                    .then(Commands.literal("hatchling").executes(context -> setAgeTicks(context.getSource(), -20000, 0)))
                    .then(Commands.literal("juvenile").executes(context -> setAgeTicks(context.getSource(), -12000, 0)))
                    .then(Commands.literal("adult").executes(context -> setAgeTicks(context.getSource(), 0, 0)))
                    .then(Commands.literal("elder").executes(context -> setAgeTicks(context.getSource(), 0, com.dragonspeech.dragon.DragonEntity.ELDER_TICKS)))
                    .then(Commands.literal("ancient").executes(context -> setAgeTicks(context.getSource(), 0, com.dragonspeech.dragon.DragonEntity.ELDER_TICKS + com.dragonspeech.dragon.DragonEntity.ANCIENT_TICKS))))));
    }

    /**
     * FIX: "they both say age 0 ticks" - elder and ancient now use a
     * genuinely separate mechanism (postAdultTicks) rather than being
     * collapsed into "just adult" - see DragonEntity's own doc on why
     * this needed its own counter rather than extending the existing
     * age field, which vanilla's own aging has no defined behavior for
     * past 0.
     */
    private static int setAgeTicks(CommandSourceStack source, int ticks, int postAdultTicks) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        Optional<DragonEntity> bonded = nearestBonded(player);
        if (bonded.isEmpty()) {
            player.sendSystemMessage(Component.literal("No bonded dragon of yours is close enough."));
            return 0;
        }
        DragonEntity dragon = bonded.get();
        dragon.setAge(ticks);
        dragon.setPostAdultTicks(postAdultTicks);
        String stageLabel = dragon.isAncient() ? "ancient" : dragon.isElder() ? "elder" : dragon.isAdult() ? "adult" : dragon.isJuvenile() ? "juvenile" : "hatchling";
        player.sendSystemMessage(Component.literal("Your " + dragon.color().getSerializedName() + " dragon is now " + stageLabel + "."));
        return 1;
    }

    private static int call(CommandSourceStack source, float requested) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        Optional<DragonEntity> bonded = nearestBonded(player);
        if (bonded.isEmpty()) {
            player.sendSystemMessage(Component.literal("No bonded dragon of yours is close enough to hear the call."));
            return 0;
        }

        DragonEntity dragon = bonded.get();
        float given = dragon.feedStaminaTo(player, requested);
        if (given <= 0f) {
            player.sendSystemMessage(Component.literal("Your dragon has nothing left to give right now."));
            return 0;
        }

        PlayerMagicData magic = StaminaAccess.get(player);
        StaminaAccess.set(player, magic.withStamina(Math.min(magic.maxStamina(), magic.stamina() + given)));
        player.sendSystemMessage(Component.literal("Your dragon answers the call. " + Math.round(given) + " stamina flows into you."));
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        List<DragonEntity> nearby = player.level().getEntitiesOfClass(DragonEntity.class,
            player.getBoundingBox().inflate(CALL_RANGE), d -> d.isBondedTo(player));
        if (nearby.isEmpty()) {
            player.sendSystemMessage(Component.literal("No bonded dragon of yours is nearby."));
            return 0;
        }
        for (DragonEntity dragon : nearby) {
            String stageLabel = dragon.isHatchling() ? "hatchling" : dragon.isJuvenile() ? "juvenile" : dragon.isAncient() ? "ancient" : dragon.isElder() ? "elder" : "adult";
            player.sendSystemMessage(Component.literal(
                dragon.color().getSerializedName() + " dragon, " + stageLabel
                    + " - " + Math.round(dragon.dragonStamina()) + "/" + Math.round(dragon.maxDragonStamina()) + " stamina"));
        }
        return 1;
    }

    private static Optional<DragonEntity> nearestBonded(ServerPlayer player) {
        AABB range = player.getBoundingBox().inflate(CALL_RANGE);
        return player.level().getEntitiesOfClass(DragonEntity.class, range, d -> d.isBondedTo(player))
            .stream()
            .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
    }
}
