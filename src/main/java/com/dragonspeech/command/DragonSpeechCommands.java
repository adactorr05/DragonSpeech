package com.dragonspeech.command;

import com.dragonspeech.growth.AttunementAccess;
import com.dragonspeech.guess.BacklashAuditLog;
import com.dragonspeech.dragon.DragonEntity;
import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import com.dragonspeech.vocabulary.PlayerVocabulary;
import com.dragonspeech.vocabulary.VocabularyAccess;
import com.dragonspeech.vocabulary.VocabularyService;
import com.dragonspeech.ward.WardService;
import com.dragonspeech.word.DiscoveryMethod;
import com.dragonspeech.word.Word;
import com.dragonspeech.word.WordRegistry;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Admin command tree (permission level 2 - same as /gamemode etc.):
 *
 *   /dragonspeech grant <player> <word>     - teach one word (respects prerequisites)
 *   /dragonspeech grantall <player>         - teach the entire registry, in dependency order
 *   /dragonspeech vocab <player>            - list what a player knows
 *   /dragonspeech resetvocab <player>       - wipe a player's vocabulary
 *   /dragonspeech stamina <player> <amount> - set a player's current stamina
 *   /dragonspeech wards <player>            - how many wards a player is carrying
 *   /dragonspeech audit                     - recent severe/catastrophic backlash events
 *
 * Grant goes through VocabularyService like every other learning path, so
 * milestones, the discovery event, and client sync all fire exactly as if
 * the word had been found legitimately - admin-granted words are not a
 * separate, subtly-different code path that can drift out of sync.
 */
public final class DragonSpeechCommands {

    private static final SuggestionProvider<CommandSourceStack> WORD_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(WordRegistry.getAllWords().keySet(), builder);

    private DragonSpeechCommands() {}

    public static void register() {
        DragonSpeechCommandRoot.add(() -> Commands.literal("grant")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("word", ResourceLocationArgument.id())
                    .suggests(WORD_SUGGESTIONS)
                    .executes(context -> grant(context.getSource(),
                        EntityArgument.getPlayer(context, "player"),
                        ResourceLocationArgument.getId(context, "word"))))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("grantall")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("players", EntityArgument.players())
                .executes(context -> grantAll(context.getSource(),
                    EntityArgument.getPlayers(context, "players")))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("vocab")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> listVocab(context.getSource(),
                    EntityArgument.getPlayer(context, "player")))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("resetvocab")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> resetVocab(context.getSource(),
                    EntityArgument.getPlayer(context, "player")))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("stamina")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("target", EntityArgument.entity())
                .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                    .executes(context -> setStamina(context.getSource(),
                        EntityArgument.getEntity(context, "target"),
                        FloatArgumentType.getFloat(context, "amount"))))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("wards")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> showWards(context.getSource(),
                    EntityArgument.getPlayer(context, "player")))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("audit")
            .requires(CommandPermissions::canUseAdmin)
            .executes(context -> audit(context.getSource())));

        DragonSpeechCommandRoot.add(() -> Commands.literal("reshufflewow")
            .requires(CommandPermissions::canUseAdmin)
            .executes(context -> reshuffleWow(context.getSource())));

        DragonSpeechCommandRoot.add(() -> Commands.literal("revealwow")
            .requires(CommandPermissions::canUseAdmin)
            .executes(context -> revealWow(context.getSource())));

        DragonSpeechCommandRoot.add(() -> Commands.literal("truename")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> trueName(context.getSource(),
                    EntityArgument.getPlayer(context, "player")))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("revealtruename")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("target", EntityArgument.entities())
                .executes(context -> revealTrueName(context.getSource(),
                    EntityArgument.getEntities(context, "target")))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("regenname")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> regenName(context.getSource(),
                    EntityArgument.getPlayer(context, "player")))));

        DragonSpeechCommandRoot.add(() -> Commands.literal("endduel")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> endDuel(context.getSource(),
                    EntityArgument.getPlayer(context, "player")))));
    }

    /** Admin-only: reveals a player's own true name in plaintext - never expose this to the player being named, or to whoever runs the command about someone else's alt/target, outside of genuine debugging. */
    private static int trueName(CommandSourceStack source, ServerPlayer target) {
        var name = com.dragonspeech.mind.TrueNameService.getOrCreate(target);
        source.sendSuccess(() -> Component.literal(
                target.getGameProfile().getName() + "'s true name (generation " + name.generation() + "): " + name.plaintext()), false);
        return 1;
    }

    /** Shows any entity's (mob or player) true name as a real nametag floating above its head for 10 seconds, then reverts - works on every entity the selector matches, mobs included, since they have true names too now. */
    private static int revealTrueName(CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets) {
        if (targets.isEmpty()) {
            source.sendFailure(Component.literal("No matching entity found."));
            return 0;
        }
        int revealed = 0;
        for (net.minecraft.world.entity.Entity target : targets) {
            if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) {
                continue;
            }
            var name = com.dragonspeech.mind.TrueNameService.getOrCreateForEntity(living);
            com.dragonspeech.mind.TrueNameRevealTicker.reveal(source.getServer(), target, name.plaintext());
            revealed++;
        }
        int finalRevealed = revealed;
        source.sendSuccess(() -> Component.literal("Revealed " + finalRevealed + " true name(s) - visible above their heads for a few seconds."), false);
        return revealed;
    }

    private static int regenName(CommandSourceStack source, ServerPlayer target) {
        var name = com.dragonspeech.mind.TrueNameService.regenerate(target);
        source.sendSuccess(() -> Component.literal(
                target.getGameProfile().getName() + "'s true name has changed (now generation " + name.generation() + "). Anyone who knew the old one has lost their hold."), true);
        return 1;
    }

    private static int endDuel(CommandSourceStack source, ServerPlayer target) {
        var duel = com.dragonspeech.mind.MindDuelManager.forParticipant(target.getUUID()).orElse(null);
        if (duel == null) {
            source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " is not in a mind duel."), false);
            return 0;
        }
        com.dragonspeech.mind.MindDuelService.end(source.getServer(), duel, com.dragonspeech.mind.DuelOutcome.INTERRUPTED, "An admin ends the duel.");
        source.sendSuccess(() -> Component.literal("Ended " + target.getGameProfile().getName() + "'s mind duel."), true);
        return 1;
    }

    private static int grant(CommandSourceStack source, ServerPlayer target, ResourceLocation wordId) {
        VocabularyService.LearnResult result = VocabularyService.learnWord(target, wordId, DiscoveryMethod.ADMIN_GRANTED);
        String message = switch (result) {
            case LEARNED -> "Granted '" + wordId + "' to " + target.getGameProfile().getName() + ".";
            case ALREADY_KNOWN -> target.getGameProfile().getName() + " already knows '" + wordId + "'.";
            case PREREQUISITES_NOT_MET -> "'" + wordId + "' has unmet prerequisites - grant those first (or use grantall).";
            case UNKNOWN_WORD -> "No word with id '" + wordId + "' exists.";
        };
        source.sendSuccess(() -> Component.literal(message), true);
        return result == VocabularyService.LearnResult.LEARNED ? 1 : 0;
    }

    /** Loops until no more words can be learned - naturally resolves prerequisite chains in dependency order without needing an explicit sort. */
    private static int grantAll(CommandSourceStack source, java.util.Collection<ServerPlayer> targets) {
        int learnedAcrossTargets = 0;
        for (ServerPlayer target : targets) {
            int learnedForTarget = 0;
            boolean progress = true;
            while (progress) {
                progress = false;
                for (ResourceLocation id : WordRegistry.getAllWords().keySet()) {
                    if (VocabularyService.learnWord(target, id, DiscoveryMethod.ADMIN_GRANTED) == VocabularyService.LearnResult.LEARNED) {
                        learnedForTarget++;
                        learnedAcrossTargets++;
                        progress = true;
                    }
                }
            }
            int finalLearnedForTarget = learnedForTarget;
            source.sendSuccess(() -> Component.literal(
                "Granted " + finalLearnedForTarget + " word(s) to " + target.getGameProfile().getName() + "."), true);
        }
        return learnedAcrossTargets;
    }

    private static int listVocab(CommandSourceStack source, ServerPlayer target) {
        Map<ResourceLocation, Word> known = VocabularyService.getKnownWordsWithIds(target);
        if (known.isEmpty()) {
            source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " knows no words."), false);
            return 0;
        }
        StringBuilder list = new StringBuilder(target.getGameProfile().getName() + " knows " + known.size() + " word(s): ");
        known.values().forEach(word -> list.append(word.trueName()).append(", "));
        String output = list.substring(0, list.length() - 2);
        source.sendSuccess(() -> Component.literal(output), false);
        return known.size();
    }

    private static int resetVocab(CommandSourceStack source, ServerPlayer target) {
        VocabularyAccess.set(target, PlayerVocabulary.empty());
        com.dragonspeech.wow.WordOfWordsKnowledge.forget(target);
        com.dragonspeech.network.VocabularySyncHooks.pushSync(target);
        source.sendSuccess(() -> Component.literal(
                "Wiped " + target.getGameProfile().getName() + "'s vocabulary."), true);
        return 1;
    }

    /** "Make sure it can be used on dragons as well" - target is now any single entity, not just a player: a ServerPlayer sets their magic stamina (unchanged from before), a DragonEntity sets ITS OWN separate stamina pool (dragonStamina, not the same system at all - see DragonEntity#setDragonStamina). Anything else is rejected with a clear message rather than silently doing nothing. */
    private static int setStamina(CommandSourceStack source, net.minecraft.world.entity.Entity target, float amount) {
        if (target instanceof ServerPlayer player) {
            PlayerMagicData magic = StaminaAccess.get(player);
            StaminaAccess.set(player, magic.withStamina(amount));
            source.sendSuccess(() -> Component.literal(
                    "Set " + player.getGameProfile().getName() + "'s stamina to " + Math.min(amount, magic.maxStamina())
                            + " (max " + magic.maxStamina() + ")."), true);
            return 1;
        } else if (target instanceof DragonEntity dragon) {
            dragon.setDragonStamina(amount);
            source.sendSuccess(() -> Component.literal(
                    "Set the " + dragon.color().getSerializedName() + " dragon's stamina to " + dragon.dragonStamina()
                            + " (max " + dragon.maxDragonStamina() + ")."), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(
                    "That's not a player or a dragon - the stamina command only applies to those two."));
            return 0;
        }
    }

    private static int showWards(CommandSourceStack source, ServerPlayer target) {
        int count = WardService.wardCount(target);
        source.sendSuccess(() -> Component.literal(
                target.getGameProfile().getName() + " carries " + count + " active ward(s)."), false);
        return count;
    }

    private static int reshuffleWow(CommandSourceStack source) {
        com.dragonspeech.wow.WowPhraseState.reshuffle(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "A new Word of Words has been generated for this world. All previous knowledge and memory-bindings were severed."), true);
        return 1;
    }

    private static int revealWow(CommandSourceStack source) {
        String word = com.dragonspeech.wow.WowPhraseState.current(source.getServer());
        source.sendSuccess(() -> Component.literal("Word of Words: " + word), false);
        return 1;
    }

    private static int audit(CommandSourceStack source) {
        var entries = BacklashAuditLog.recent(10);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No serious backlash events recorded since server start."), false);
            return 0;
        }
        for (BacklashAuditLog.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "[t=%d] %s - %s backlash (x%.2f severity)",
                    entry.gameTime(), entry.playerName(), entry.tier(), entry.severityScale())), false);
        }
        return entries.size();
    }
}