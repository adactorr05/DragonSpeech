package com.dragonspeech.dragon;

import com.dragonspeech.command.DragonSpeechCommandRoot;
import com.dragonspeech.command.CommandPermissions;
import com.dragonspeech.DragonSpeech;
import com.dragonspeech.dragon.breed.DragonBreed;
import com.dragonspeech.dragon.breed.DragonBreedRegistry;
import com.dragonspeech.mind.EntityLookup;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * "Only the fire dragon is summoned [via vanilla /summon]... make a
 * summon for each type of dragon" per explicit direction. Real cause
 * of the fire-only behavior: vanilla's own /summon just calls
 * DragonEntity::new with no breed set at all, and
 * DragonEntity.readAdditionalSaveData()'s own fallback defaults to
 * "fire" specifically when no breed NBT is present at all - not a bug
 * in that fallback (a reasonable default for genuinely unset data),
 * just not what you want for actually choosing a breed to summon.
 *
 * A single "/dragonspeech summon <breed>" with tab-completed breed
 * names, not 7 separate command literals - simpler to maintain (new
 * breeds from a datapack/addon show up in tab-completion automatically
 * via DragonBreedRegistry.getAllBreeds(), no new command needed) and
 * exactly as easy to use.
 */
public final class DragonSummonCommand {
    private DragonSummonCommand() {}

    private static final SuggestionProvider<CommandSourceStack> BREED_SUGGESTIONS = (context, builder) ->
        SharedSuggestionProvider.suggest(
            DragonBreedRegistry.getAllBreeds().keySet().stream()
                .map(id -> DragonSpeech.MOD_ID.equals(id.getNamespace()) ? id.getPath() : id.toString())
                .sorted(),
            builder
        );

    public static void register() {
        DragonSpeechCommandRoot.add(() -> Commands.literal("summon")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("breed", ResourceLocationArgument.id())
                .suggests(BREED_SUGGESTIONS)
                .executes(ctx -> summon(ctx.getSource(), ResourceLocationArgument.getId(ctx, "breed"), false))
                .then(Commands.literal("nobond")
                    .executes(ctx -> summon(ctx.getSource(), ResourceLocationArgument.getId(ctx, "breed"), false)))
                .then(Commands.literal("bond")
                    .executes(ctx -> summon(ctx.getSource(), ResourceLocationArgument.getId(ctx, "breed"), true)))));
    }

    private static int summon(CommandSourceStack source, ResourceLocation breedInput, boolean bond) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a player can use this."));
            return 0;
        }

        // ResourceLocationArgument parses a bare "fire" as minecraft:fire.
        // For convenience, if no breed exists under that exact id, a bare/default-namespace
        // value falls back to dragonspeech:<path>. Full addon ids remain exact.
        ResourceLocation breedId = breedInput;
        DragonBreed breed = DragonBreedRegistry.get(breedId);
        if (breed == null && "minecraft".equals(breedInput.getNamespace())) {
            ResourceLocation builtIn = DragonSpeech.id(breedInput.getPath());
            DragonBreed builtInBreed = DragonBreedRegistry.get(builtIn);
            if (builtInBreed != null) {
                breedId = builtIn;
                breed = builtInBreed;
            }
        }
        if (breed == null) {
            player.sendSystemMessage(Component.literal("Unknown breed: '" + breedInput + "'. Known breeds: "
                + String.join(", ", DragonBreedRegistry.getAllBreeds().keySet().stream().map(id -> DragonSpeech.MOD_ID.equals(id.getNamespace()) ? id.getPath() : id.toString()).sorted().toList())));
            return 0;
        }

        ServerLevel level = player.serverLevel();

        if (bond) {
            PlayerBondData bondData = PlayerBondAccess.get(player);
            boolean livingBond = bondData.currentBondedDragon().isPresent()
                && EntityLookup.byUUID(level.getServer(), bondData.currentBondedDragon().get()) instanceof DragonEntity existing
                && existing.isAlive()
                && existing.bondedOwner().filter(player.getUUID()::equals).isPresent();
            boolean rebondBlocked = bondData.hasHadBondedDragonDie()
                && !com.dragonspeech.config.DragonSpeechConfig.allowRebondAfterDeath();
            if (livingBond) {
                player.sendSystemMessage(Component.literal("You already have a living bonded dragon. Use nobond for a test spawn instead of creating a second bond."));
                return 0;
            }
            if (rebondBlocked) {
                player.sendSystemMessage(Component.literal("This world's bond rules do not allow another dragon after a bonded dragon has died."));
                return 0;
            }
        }

        DragonEntity dragon = com.dragonspeech.entity.DragonSpeechEntities.DRAGON.create(level);
        if (dragon == null) {
            return 0;
        }
        // Match Dragon Flux's useful debug behavior: spawn in front of the caster rather than
        // intersecting the player's hitbox.
        var look = player.getLookAngle();
        dragon.moveTo(player.getX() + look.x * 3.0, player.getY(), player.getZ() + look.z * 3.0, player.getYRot(), 0f);
        dragon.setBreedId(breedId);
        if (bond) dragon.setBondedOwner(player.getUUID());
        level.addFreshEntity(dragon);

        if (bond) {
            PlayerBondAccess.set(player, PlayerBondAccess.get(player).withCurrentBondedDragon(dragon.getUUID()));
        }
        player.sendSystemMessage(Component.literal("Summoned a " + breedId.getPath() + " dragon"
            + (bond ? " and bonded it to you." : " with no bond.")));
        return 1;
    }
}
