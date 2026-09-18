package com.dragonspeech.mob.casting;

import com.dragonspeech.command.DragonSpeechCommandRoot;
import com.dragonspeech.command.CommandPermissions;
import com.dragonspeech.mind.MindBreachAdmin;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * "/dragonspeech debug <subcommand> <true|false>" - a single unified
 * tree for every admin debug toggle, per explicit direction. Each
 * subcommand just delegates to the SAME underlying service its
 * standalone command (if any) already uses - there's exactly one source
 * of truth for "is showwards on for this player" whether you toggle it
 * via "/dragonspeech showwards true" or "/dragonspeech debug showwards
 * true", not two separate booleans that could disagree.
 *
 * - npcspells: NpcSpellDebug - announces every word a mob casts, and at
 *   whom, the instant it casts it.
 * - staminaview: StaminaView - admin-only look-at-target stamina bar.
 * - forcebreach: MindBreachAdmin - same as the standalone
 *   "/dragonspeech forcebreach" command.
 * - showwards: WardVisibility - same as the standalone
 *   "/dragonspeech showwards" command.
 * - audit: EntityAudit - full diagnostic dump on right-click, not a
 *   look-based toggle like the others but the same true/false shape to
 *   turn it on and off.
 */
public final class DebugCommand {
    private DebugCommand() {}

    public static void register() {
        DragonSpeechCommandRoot.add(() -> Commands.literal("debug")
                .requires(CommandPermissions::canUseAdmin)
                .then(subcommand("npcspells", "NPC spell announcements", NpcSpellDebug::set))
                .then(subcommand("staminaview", "Stamina view", StaminaView::set))
                .then(subcommand("forcebreach", "Force mind breach", MindBreachAdmin::set))
                .then(subcommand("showwards", "Ward visibility", WardVisibility::set))
                .then(subcommand("audit", "Entity audit (right-click to dump)", EntityAudit::set)));
    }

    @FunctionalInterface
    private interface Toggle {
        void set(ServerPlayer player, boolean enabled);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> subcommand(
            String name, String label, Toggle toggle) {
        return Commands.literal(name)
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            toggle.set(player, enabled);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    label + " " + (enabled ? "enabled." : "disabled.")), false);
                            return 1;
                        })
                );
    }
}
