package com.dragonspeech.command;

import com.dragonspeech.race.PlayerRaceData;
import com.dragonspeech.race.RaceAccess;
import com.dragonspeech.race.RaceTraitHooks;
import com.dragonspeech.race.RaceType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Player-facing (no permission requirement - choosing an origin is
 * something any player does for themselves, unlike the admin tools in
 * DragonSpeechCommands):
 *
 *   /race choose <human|elf|dwarf|urgal>  - once only; see chooseRace
 *   /race info                            - your current race, its traits, and the domain discount
 */
public final class RaceCommands {

    private RaceCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("race")
                .then(Commands.literal("choose")
                    .then(Commands.argument("race", StringArgumentType.word())
                        .executes(context -> chooseRace(context.getSource(),
                            StringArgumentType.getString(context, "race")))))
                .then(Commands.literal("info")
                    .executes(context -> showInfo(context.getSource())))
            ));
    }

    private static int chooseRace(CommandSourceStack source, String raceName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerRaceData current = RaceAccess.get(player);

        if (current.race().isPresent()) {
            source.sendFailure(Component.literal(
                "You are already " + current.race().get().name().toLowerCase(Locale.ROOT) + " - an origin, once claimed, cannot be unclaimed."));
            return 0;
        }

        RaceType chosen;
        try {
            chosen = RaceType.valueOf(raceName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown origin '" + raceName + "'. Choices: human, elf, dwarf, urgal."));
            return 0;
        }

        RaceAccess.set(player, new PlayerRaceData(java.util.Optional.of(chosen)));
        RaceTraitHooks.apply(player);

        source.sendSuccess(() -> Component.literal(
            "You are " + chosen.name().toLowerCase(Locale.ROOT) + ". " + chosen.description()), false);
        return 1;
    }

    private static int showInfo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var race = RaceAccess.get(player).race();

        if (race.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                "You have not yet claimed an origin. Use /race choose <human|elf|dwarf|urgal> - choose once, and choose for good."), false);
            return 0;
        }

        RaceType r = race.get();
        String affinities = r.affinities().isEmpty() ? "none (but attunement grows 50% faster in every domain)"
            : r.affinities().stream().map(Enum::name).reduce((a, b) -> a + ", " + b).orElse("none");
        source.sendSuccess(() -> Component.literal(
            r.name() + " - " + r.description() + "\nDiscounted domains: " + affinities), false);
        return 1;
    }
}
