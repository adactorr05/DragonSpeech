package com.dragonspeech.command;

import com.dragonspeech.mind.ActiveMindDuel;
import com.dragonspeech.mind.ContactResolver;
import com.dragonspeech.mind.DuelAction;
import com.dragonspeech.mind.DuelActionResult;
import com.dragonspeech.mind.MindDuelActionService;
import com.dragonspeech.mind.MindDuelManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Everyday player commands for Phase 6, permission level 0 - the mind
 * duel's chat-based interface, same role ChatCastHooks plays for
 * ordinary spellcasting. A future client screen (see the design notes'
 * per-phase mockups) is meant to sit ALONGSIDE this, not replace it -
 * every action below goes through the exact same MindDuelActionService
 * a screen's buttons would call, so nothing here is a separate,
 * potentially-drifting code path.
 *
 *   /mind reach <target>    - attempt Contact (requires the hugleita skill) - auto-detects a linked target and starts a team duel instead
 *   /mind action <action>   - take one action in your current 1v1 duel (e.g. pressure, reinforce, assault)
 *   /mind command <id>      - issue a specific command during Occupied Mind / True Name Domination
 *   /mind click <bar> <x> <y> - attacker debug command: click the barrier at normalized coordinates
 *   /mind click <bar> <crackId> - defender debug command: mend a visible crack
 *   /mind link invite <players...> - invite other players to link minds with you
 *   /mind link accept <inviter>    - accept a pending link invite from that player
 *   /mind link leave        - withdraw from your current mind link
 *   /mind link status       - show who you're linked with and the link's strength
 *   /mind team <action> [target] - take one action in your current TEAM duel
 *   /mind status            - show your current duel's numbers
 *   /mind disengage         - withdraw from your current 1v1 duel
 */
public final class MindCommands {

    private MindCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("mind")

                .then(Commands.literal("reach")
                    .then(Commands.argument("target", EntityArgument.entity())
                        .executes(context -> reach(context.getSource(),
                            EntityArgument.getEntity(context, "target")))))

                .then(Commands.literal("action")
                    .then(Commands.argument("action", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (DuelAction action : DuelAction.values()) {
                                builder.suggest(action.getSerializedName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> action(context.getSource(),
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "action"), ""))))

                .then(Commands.literal("command")
                    .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (String id : com.dragonspeech.mind.CommandEffectRegistry.all().keySet()) {
                                builder.suggest(id);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> action(context.getSource(), "issue_command",
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id")))))

                .then(Commands.literal("click")
                    .then(Commands.argument("bar", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (com.dragonspeech.mind.BarType bar : com.dragonspeech.mind.BarType.values()) {
                                builder.suggest(bar.getSerializedName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("crackId", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                            .executes(context -> clickCrack(context.getSource(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "bar"),
                                String.valueOf(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "crackId")))))
                        .then(Commands.argument("x", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0f, 1f))
                            .then(Commands.argument("y", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0f, 1f))
                                .executes(context -> clickCrack(context.getSource(),
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "bar"),
                                    com.mojang.brigadier.arguments.FloatArgumentType.getFloat(context, "x")
                                        + ":" + com.mojang.brigadier.arguments.FloatArgumentType.getFloat(context, "y")))))))

                .then(Commands.literal("seize")
                    .executes(context -> action(context.getSource(), "seize_control", "")))

                .then(Commands.literal("link")
                    .then(Commands.literal("invite")
                        .then(Commands.argument("players", EntityArgument.players())
                            .executes(context -> linkInvite(context.getSource(),
                                EntityArgument.getPlayers(context, "players")))))
                    .then(Commands.literal("accept")
                        .then(Commands.argument("inviter", EntityArgument.player())
                            .executes(context -> linkAccept(context.getSource(),
                                EntityArgument.getPlayer(context, "inviter")))))
                    .then(Commands.literal("leave")
                        .executes(context -> linkLeave(context.getSource())))
                    .then(Commands.literal("status")
                        .executes(context -> linkStatus(context.getSource()))))

                .then(Commands.literal("team")
                    .then(Commands.argument("action", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (com.dragonspeech.mind.TeamDuelAction action : com.dragonspeech.mind.TeamDuelAction.values()) {
                                builder.suggest(action.getSerializedName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> teamAction(context.getSource(),
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "action"), null))
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(context -> teamAction(context.getSource(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "action"),
                                EntityArgument.getPlayer(context, "target"))))))

                .then(Commands.literal("status")
                    .executes(context -> status(context.getSource())))

                .then(Commands.literal("disengage")
                    .executes(context -> action(context.getSource(), "disengage", "")))

                .then(Commands.literal("grimoire")
                    .executes(context -> grimoire(context.getSource())))

                .then(Commands.literal("connect")
                    .then(Commands.argument("targetId", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(context -> connect(context.getSource(),
                            com.mojang.brigadier.arguments.StringArgumentType.getString(context, "targetId")))))
            ));
    }

    /**
     * Lists every true name in the caller's grimoire as a clickable chat
     * line - clicking one runs /mind connect <targetId>, which is the
     * "click their true name in the grimoire to open the mind connected
     * screen" the design calls for. A real dedicated GUI tab would be a
     * nicer home for this eventually; clickable chat text was chosen
     * over building a whole new custom Screen for it, since it reuses
     * vanilla's own click-to-run-command support rather than adding more
     * surface area to an already-large client package.
     */
    private static int grimoire(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var data = com.dragonspeech.mind.MindDataAccess.get(player);
        if (data.learnedNames().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Your grimoire holds no true names yet."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("--- Your Grimoire ---"), false);
        for (var entry : data.learnedNames().entrySet()) {
            java.util.UUID targetId = entry.getKey();
            String label;
            var server = source.getServer();
            var entity = com.dragonspeech.mind.EntityLookup.byUUID(server, targetId);
            label = entity != null ? entity.getName().getString() : "(not currently present)";
            Component line = Component.literal("- " + label)
                .withStyle(style -> style
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/mind connect " + targetId))
                    .withUnderlined(true));
            source.sendSuccess(() -> line, false);
        }
        return data.learnedNames().size();
    }

    /** Reconnects instantly to a grimoire entry - the target must currently be loaded/present; a name learned about something not around right now simply can't be interacted with until it is, same as any other entity-targeted action in this mod. */
    private static int connect(CommandSourceStack source, String rawTargetId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        java.util.UUID targetId;
        try {
            targetId = java.util.UUID.fromString(rawTargetId);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("That is not a valid grimoire entry."));
            return 0;
        }
        if (com.dragonspeech.mind.MindDataAccess.get(player).learnedAbout(targetId).isEmpty()) {
            source.sendFailure(Component.literal("That name is not in your grimoire."));
            return 0;
        }
        var target = com.dragonspeech.mind.EntityLookup.byUUID(source.getServer(), targetId);
        if (!(target instanceof LivingEntity livingTarget)) {
            source.sendFailure(Component.literal("They are not currently present - try again when they are."));
            return 0;
        }
        if (!com.dragonspeech.mind.TrueNameService.knowsEntity(player, livingTarget)) {
            source.sendFailure(Component.literal("That name no longer holds power over them - it may have changed since you learned it."));
            return 0;
        }
        var outcome = ContactResolver.instantConnectViaTrueName(source.getServer(), player, livingTarget);
        return outcome.success() || outcome.pending() ? 1 : 0;
    }

    private static int reach(CommandSourceStack source, net.minecraft.world.entity.Entity target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(target instanceof LivingEntity livingTarget)) {
            source.sendFailure(Component.literal("That has no mind to reach."));
            return 0;
        }
        ContactResolver.ContactOutcome outcome = ContactResolver.attempt(player, livingTarget);
        // ContactResolver already messages the player for every roll-based
        // outcome; only the early-gate failures (no skill, on cooldown,
        // already in a duel) haven't spoken yet by the time we get here.
        if (!outcome.success()) {
            source.sendSuccess(() -> Component.literal(outcome.message()), false);
        }
        return outcome.success() ? 1 : 0;
    }

    private static int clickCrack(CommandSourceStack source, String rawBar, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ActiveMindDuel duel = MindDuelManager.forParticipant(player.getUUID()).orElse(null);
        if (duel == null) {
            source.sendFailure(Component.literal("You are not currently in a mind duel."));
            return 0;
        }
        try {
            com.dragonspeech.mind.BarType.valueOf(rawBar.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("'" + rawBar + "' is not a recognized card."));
            return 0;
        }
        // Which action this is follows from the argument shape, not the
        // player's duel role any more - both roles can strike (a barrier
        // position, "x:y") and seal (an existing crack id) now. The
        // command tree above only ever calls this with one shape or the
        // other, matching how it's registered.
        DuelAction action = target.contains(":") ? DuelAction.STRIKE_CRACK : DuelAction.SEAL_CRACK;
        String param = rawBar.toLowerCase() + ":" + target;
        DuelActionResult result = MindDuelActionService.resolve(source.getServer(), duel, player.getUUID(), action, param);
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return result.legal() ? 1 : 0;
    }

    private static int action(CommandSourceStack source, String rawAction, String param) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ActiveMindDuel duel = MindDuelManager.forParticipant(player.getUUID()).orElse(null);
        if (duel == null) {
            source.sendFailure(Component.literal("You are not currently in a mind duel."));
            return 0;
        }

        DuelAction action;
        try {
            action = DuelAction.valueOf(rawAction.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("'" + rawAction + "' is not a recognized action."));
            return 0;
        }

        DuelActionResult result = MindDuelActionService.resolve(source.getServer(), duel, player.getUUID(), action, param);
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return result.legal() ? 1 : 0;
    }

    private static int linkInvite(CommandSourceStack source, java.util.Collection<ServerPlayer> targets) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int count = 0;
        for (ServerPlayer target : targets) {
            if (target.getUUID().equals(player.getUUID())) {
                continue;
            }
            com.dragonspeech.mind.PendingLinkInvites.invite(player.getUUID(), target.getUUID());
            target.sendSystemMessage(Component.literal(
                player.getGameProfile().getName() + " invites you to link minds for mutual defense. Run /mind link accept "
                    + player.getGameProfile().getName() + " to accept."));
            count++;
        }
        int finalCount = count;
        source.sendSuccess(() -> Component.literal("Sent " + finalCount + " mind-link invite(s)."), false);
        return count;
    }

    private static int linkAccept(CommandSourceStack source, ServerPlayer inviter) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        java.util.UUID pendingFrom = com.dragonspeech.mind.PendingLinkInvites.consume(player.getUUID());
        if (pendingFrom == null || !pendingFrom.equals(inviter.getUUID())) {
            source.sendFailure(Component.literal("You have no pending link invite from " + inviter.getGameProfile().getName() + "."));
            return 0;
        }
        var link = com.dragonspeech.mind.MindLinkManager.join(inviter.getUUID(), player.getUUID());
        String names = link.memberIds().stream()
            .map(id -> source.getServer().getPlayerList().getPlayer(id))
            .filter(java.util.Objects::nonNull)
            .map(p -> p.getGameProfile().getName())
            .collect(java.util.stream.Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("You join the mind link. Linked with: " + names), true);
        inviter.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " has joined your mind link."));
        return 1;
    }

    private static int linkLeave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        com.dragonspeech.mind.MindLinkManager.leave(player.getUUID());
        source.sendSuccess(() -> Component.literal("You withdraw from your mind link."), false);
        return 1;
    }

    private static int linkStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var link = com.dragonspeech.mind.MindLinkManager.get(player.getUUID());
        if (link.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You are not part of any mind link."), false);
            return 0;
        }
        String names = link.get().memberIds().stream()
            .map(id -> source.getServer().getPlayerList().getPlayer(id))
            .filter(java.util.Objects::nonNull)
            .map(p -> p.getGameProfile().getName())
            .collect(java.util.stream.Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal(String.format(
            "Linked with: %s | Link Strength: %.0f%%", names, link.get().linkStrength())), false);
        return 1;
    }

    private static int teamAction(CommandSourceStack source, String rawAction, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var duel = com.dragonspeech.mind.TeamMindDuelManager.forParticipant(player.getUUID()).orElse(null);
        if (duel == null) {
            source.sendFailure(Component.literal("You are not currently in a team mind duel."));
            return 0;
        }

        com.dragonspeech.mind.TeamDuelAction action;
        try {
            action = com.dragonspeech.mind.TeamDuelAction.valueOf(rawAction.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("'" + rawAction + "' is not a recognized team action."));
            return 0;
        }

        DuelActionResult result = com.dragonspeech.mind.TeamMindDuelService.resolve(
            source.getServer(), duel, player.getUUID(), action, target != null ? target.getUUID() : null);
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return result.legal() ? 1 : 0;
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ActiveMindDuel duel = MindDuelManager.forParticipant(player.getUUID()).orElse(null);
        if (duel == null) {
            source.sendSuccess(() -> Component.literal("You are not currently in a mind duel."), false);
            return 0;
        }

        boolean isAttacker = duel.isAttacker(player.getUUID());
        var self = isAttacker ? duel.attacker() : duel.defender();
        var opponent = isAttacker ? duel.defender() : duel.attacker();
        float myBarrier = isAttacker ? duel.attackerBarrierIntegrity() : duel.defenderBarrierIntegrity();
        float theirBarrier = isAttacker ? duel.defenderBarrierIntegrity() : duel.attackerBarrierIntegrity();

        source.sendSuccess(() -> Component.literal(String.format(
            "Phase: %s | You are the %s | Your Focus %.0f/%.0f, Stamina %.0f/%.0f | Their Focus %.0f/%.0f, Stamina %.0f/%.0f | Your Barrier %.0f%% | Their Barrier %.0f%% | Control %.0f",
            duel.phase(), isAttacker ? "attacker" : "defender",
            self.focus(), self.maxFocus(), self.stamina(), self.maxStamina(),
            opponent.focus(), opponent.maxFocus(), opponent.stamina(), opponent.maxStamina(),
            myBarrier, theirBarrier, isAttacker ? duel.controlAdvantage() : -duel.controlAdvantage())), false);
        return 1;
    }
}
