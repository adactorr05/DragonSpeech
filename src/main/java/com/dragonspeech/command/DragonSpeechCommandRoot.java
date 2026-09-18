package com.dragonspeech.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Owns the single /dragonspeech Brigadier root.
 *
 * Older builds registered the same literal root independently from many
 * classes. Brigadier can merge duplicate literal nodes, but doing that with
 * different requirements and two competing "summon <string>" branches made
 * permissions and tab completion fragile. Every Dragon Speech subcommand now
 * contributes a fresh child builder here and this class registers the root
 * exactly once.
 */
public final class DragonSpeechCommandRoot {
    private static final List<Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> CHILDREN = new ArrayList<>();
    private static boolean eventRegistered;

    private DragonSpeechCommandRoot() {}

    public static synchronized void add(Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>> childFactory) {
        CHILDREN.add(childFactory);
        if (!eventRegistered) {
            eventRegistered = true;
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                var root = Commands.literal("dragonspeech")
                    .executes(ctx -> showHelp(ctx.getSource()));
                for (var factory : CHILDREN) {
                    root.then(factory.get());
                }
                dispatcher.register(root);
            });
        }
    }

    private static int showHelp(CommandSourceStack source) {
        if (CommandPermissions.canUseAdmin(source)) {
            source.sendSuccess(() -> Component.literal(
                "Dragon Speech: press Tab after /dragonspeech to browse commands. Dragon breeds are under /dragonspeech summon <breed> [bond|nobond]."), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                "Dragon Speech is loaded. Administrative /dragonspeech commands require operator permission on multiplayer servers."), false);
        }
        return 1;
    }
}
