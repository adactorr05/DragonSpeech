package com.dragonspeech.mind;

import com.dragonspeech.command.DragonSpeechCommandRoot;
import com.dragonspeech.command.CommandPermissions;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * "/dragonspeech forcebreach <true|false>" - admin-only (permission
 * level 2), default false, same shape as WardVisibilityCommand. See
 * MindBreachAdmin for what happens once enabled.
 */
public final class MindBreachCommand {
    private MindBreachCommand() {}

    public static void register() {
        DragonSpeechCommandRoot.add(() -> Commands.literal("forcebreach")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(ctx -> {
                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    MindBreachAdmin.set(player, enabled);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "Force mind-breach " + (enabled ? "enabled - your next right-click on a living entity breaches its mind instantly."
                            : "disabled") + "."), false);
                    return 1;
                })));
    }
}
