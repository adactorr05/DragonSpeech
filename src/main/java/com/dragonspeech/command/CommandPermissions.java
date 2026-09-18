package com.dragonspeech.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared permission rule for Dragon Speech's administrative/debug commands.
 *
 * Dedicated servers keep the normal vanilla permission-level-2 requirement.
 * The owner of an integrated single-player world is also allowed so the mod's
 * debug/admin commands remain usable while testing a world even when the
 * vanilla cheat flag was not enabled when that save was created.
 */
public final class CommandPermissions {
    private CommandPermissions() {}

    public static boolean canUseAdmin(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        return source.getServer().isSingleplayerOwner(player.getGameProfile());
    }
}
