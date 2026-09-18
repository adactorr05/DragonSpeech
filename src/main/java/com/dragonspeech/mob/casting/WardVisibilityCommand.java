package com.dragonspeech.mob.casting;

import com.dragonspeech.command.DragonSpeechCommandRoot;
import com.dragonspeech.command.CommandPermissions;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * "/dragonspeech showwards <true|false>" - admin-only (permission level
 * 2, matching vanilla's own op-command threshold), default false per
 * direction.
 *
 * VERSION-RISK NOTE: CommandRegistrationCallback is this file's one
 * fabric-api touch point - stable API, but if your exact fabric-api
 * version namespaces command registration differently, this is the
 * class to check first.
 */
public final class WardVisibilityCommand {
    private WardVisibilityCommand() {}

    public static void register() {
        DragonSpeechCommandRoot.add(() -> Commands.literal("showwards")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(ctx -> {
                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    WardVisibility.set(player, enabled);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "Ward visibility " + (enabled ? "enabled" : "disabled") + "."), false);
                    return 1;
                })));
    }
}
