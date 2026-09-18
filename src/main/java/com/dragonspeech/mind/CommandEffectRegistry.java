package com.dragonspeech.mind;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Registered commands, roughly matching the design notes' own difficulty
 * table (image 5): "Drop weapon" (20), "Walk forward" (10), "Stop
 * fighting" (15), plus "Stun" as a heavier option not in that table.
 * baseDifficulty feeds ResistanceCheck exactly the way the design notes'
 * "Normal vs. True Name" columns do - True Name Domination divides it by
 * 20 regardless of which command was picked (see ResistanceCheck).
 *
 * Every apply() below works for BOTH players and mobs where that makes
 * sense (LivingEntity-level APIs only) - a duel's defender is just as
 * likely to be a mob as a player.
 */
public final class CommandEffectRegistry {

    private static final Map<String, CommandEffect> COMMANDS = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final Map<java.util.UUID, Integer> DRAIN_COUNTS = new HashMap<>();
    private static final int DRAIN_DAMAGE_THRESHOLD = 3; // "a few" drains before it starts costing real health

    /**
     * "Learn Word" (see registration below) can, rarely, hand over the
     * defender's actual TRUE NAME instead of an Ancient Language word -
     * per explicit direction, ONLY when the defender is a player who
     * "knows their own true name." The codebase's existing proxy for that
     * is PlayerMindData.ownName().isPresent() - a TrueName record has
     * been generated and stored for them. Worth being explicit about what
     * that does and doesn't mean today: TrueNameService.getOrCreate()
     * generates one lazily on ANY first access, including a third party
     * trying to guess/learn IT, not only a deliberate self-reveal to the
     * owner (there's no ritual/quest reveal mechanic yet - TrueName's own
     * class doc calls that future work). So this check is closer to
     * "their name has surfaced at all" than strictly "they were personally
     * told it," until such a reveal mechanic exists to tighten it further.
     */
    private static final float TRUE_NAME_BONUS_CHANCE = 0.10f;

    private CommandEffectRegistry() {}

    public static void bootstrap() {
        register(new CommandEffect("drop_weapon", "Drop your weapon", 20f, (server, defender, attacker) -> {
            ItemStack held = defender.getMainHandItem();
            if (!held.isEmpty()) {
                defender.spawnAtLocation(held.copy());
                defender.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            message(defender, "Your hand opens on its own. The weapon falls.");
        }));

        register(new CommandEffect("walk_forward", "Walk forward", 10f, (server, defender, attacker) -> {
            var look = defender.getLookAngle();
            defender.setDeltaMovement(look.x * 0.5, defender.getDeltaMovement().y, look.z * 0.5);
            message(defender, "Your feet move without you.");
        }));

        register(new CommandEffect("stop_fighting", "Stop fighting", 15f, (server, defender, attacker) -> {
            if (defender instanceof Mob mob) {
                mob.setTarget(null);
                mob.setAggressive(false);
            }
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 8, 1, false, true));
            message(defender, "The will to fight drains out of you, all at once.");
        }));

        register(new CommandEffect("stun", "Freeze in place", 30f, (server, defender, attacker) -> {
            // No true "cannot act at all" freeze exists here without a
            // dedicated mixin - this is the practical approximation:
            // heavy Slowness + Mining Fatigue + Weakness for a few seconds.
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 4, 9, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 4, 9, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 4, 2, false, true));
            message(defender, "Your body simply... stops.");
        }));

        register(new CommandEffect("silence", "Disable spellcasting", 25f, (server, defender, attacker) -> {
            if (defender instanceof ServerPlayer defenderPlayer) {
                long until = server.overworld().getGameTime() + 20L * 20; // 20 seconds
                MindSilence.silence(defenderPlayer.getUUID(), until);
                message(defender, "The words of the Ancient Language slip away from you, just out of reach.");
            }
        }));

        register(new CommandEffect("confuse", "Twist their emotions", 22f, (server, defender, attacker) -> {
            // The "changing emotions" category - a real emotional/mental
            // effect rather than a physical one, rounding out the set
            // alongside drop_weapon (items), walk_forward/stop_fighting/
            // stun (control), and silence. Nausea is vanilla's closest
            // built-in "your senses betray you" effect; a dedicated
            // custom confusion status would be a natural upgrade later.
            defender.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 8, 0, false, true));
            message(defender, "Your own feelings turn against you, and you can no longer tell which are truly yours.");
        }));

        register(new CommandEffect("mind_control", "Control", 20f, (server, defender, attacker) -> {
            // Config GUI (Server tab) "Allow Controlling Players" - Possession (hambinda) is already
            // unconditionally mob-only elsewhere; THIS is the actual path that can puppeteer a real
            // player's body, so it's the one this toggle needs to gate. Defaults OFF.
            if (defender instanceof ServerPlayer && !com.dragonspeech.config.DragonSpeechConfig.allowControlOfPlayers()) {
                message(defender, "Something presses at the edge of your mind, and slips away - it cannot take hold of you here.");
                message(attacker, "You reach for their body, but it will not yield to you on this server.");
                return;
            }
            MindControlService.start(server, attacker, defender, 20L * 30L);
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 2, 1, false, true));
            message(defender, "Another will presses into your body. Fight it when the pressure spikes.");
            message(attacker, "You seize their body. Move, look, attack, and break through their body; they get a resistance check every 10 seconds.");
        }));

        register(new CommandEffect("inspect_inventory", "Inventory", 0f, (server, defender, attacker) -> {
            if (!(attacker instanceof ServerPlayer attackerPlayer)) {
                return;
            }
            // A real GUI, not a chat summary - a read-only snapshot (copies,
            // not live references) opened in a plain vanilla chest-style
            // menu. Opens even when totally empty, per the request - an
            // empty container is still a completely normal, valid thing
            // to open in Minecraft.
            net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(54);
            int index = 0;
            if (defender instanceof ServerPlayer defenderPlayer) {
                for (ItemStack stack : defenderPlayer.getInventory().items) {
                    if (index >= 54) break;
                    if (!stack.isEmpty()) container.setItem(index++, stack.copy());
                }
            }
            // Equipment (mainhand/offhand/armor) via the same LivingEntity-level
            // API for EITHER a player or a mob - a skeleton's bow, a
            // vindicator's axe, worn armor, or a player's own gear all read
            // the same way, rather than needing two separate code paths.
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (index >= 54) break;
                ItemStack stack = defender.getItemBySlot(slot);
                if (!stack.isEmpty()) container.setItem(index++, stack.copy());
            }

            String title = defender.getName().getString() + "'s Inventory";
            attackerPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, playerInventory, p) -> net.minecraft.world.inventory.ChestMenu.sixRows(containerId, playerInventory, container),
                Component.literal(title)
            ));
        }));

        // Simplified to a single option per the current design: draining
        // doesn't just remove the target's stamina, it hands that
        // stamina TO the attacker's own out-of-duel stamina pool. A
        // player defender gives up a real chunk of their actual pool; a
        // mob has no stamina pool to take FROM, so the amount granted
        // instead scales with how sentient/capable it is (see
        // SentienceTier.baseFortitude()) - a Warden is worth much more
        // than a chicken.
        //
        // Per the books, stamina IS life force - draining it isn't free
        // forever. The first few drains against any one target are just
        // stamina; past DRAIN_DAMAGE_THRESHOLD, each further drain also
        // tears away real health, escalating toward actually killing the
        // target if drained enough times. DRAIN_COUNTS is keyed by
        // defender UUID and isn't cleared between separate duels against
        // the same target - a deliberate simplification, not tracked
        // per-duel, since CommandEffect's Apply interface doesn't carry
        // the ActiveMindDuel through to here.
        register(new CommandEffect("drain_stamina", "Drain Stamina", 18f, (server, defender, attacker) -> {
            if (!(attacker instanceof ServerPlayer attackerPlayer)) {
                return;
            }
            float granted;
            if (defender instanceof ServerPlayer defenderPlayer) {
                var defenderStamina = com.dragonspeech.stamina.StaminaAccess.get(defenderPlayer);
                granted = Math.max(6f, defenderStamina.maxStamina() * 0.2f);
                com.dragonspeech.stamina.StaminaAccess.set(defenderPlayer, defenderStamina.withStamina(Math.max(0f, defenderStamina.stamina() - granted)));
                com.dragonspeech.network.DragonSpeechNetworking.sendStaminaSync(defenderPlayer, defenderStamina.stamina() - granted < 0f ? 0f : defenderStamina.stamina() - granted, defenderStamina.maxStamina());
                message(defender, "Your stamina is pulled sharply away.");
            } else {
                SentienceTier tier = MindFortitudeService.classifyTier(defender);
                granted = Math.max(2f, tier.baseFortitude() * 0.4f);
            }
            var attackerStamina = com.dragonspeech.stamina.StaminaAccess.get(attackerPlayer);
            float newAttackerStamina = Math.min(attackerStamina.maxStamina(), attackerStamina.stamina() + granted);
            com.dragonspeech.stamina.StaminaAccess.set(attackerPlayer, attackerStamina.withStamina(newAttackerStamina));
            // Immediate HUD update - without this, the attacker's own
            // stamina bar wouldn't visibly move until the next routine
            // sync tick, which is what made the drain feel like it
            // wasn't doing anything at all.
            com.dragonspeech.network.DragonSpeechNetworking.sendStaminaSync(attackerPlayer, newAttackerStamina, attackerStamina.maxStamina());

            int count = DRAIN_COUNTS.merge(defender.getUUID(), 1, Integer::sum);
            attackerPlayer.sendSystemMessage(Component.literal(String.format("You draw %.0f stamina from their mind into your own.", granted)));
            if (count > DRAIN_DAMAGE_THRESHOLD) {
                float damage = 1.0f + (count - DRAIN_DAMAGE_THRESHOLD) * 0.5f; // escalates with repeated draining
                defender.hurt(defender.damageSources().magic(), damage);
                attackerPlayer.sendSystemMessage(Component.literal("Their stamina is spent - what you're drawing now is their life force itself."));
            }
        }));

        register(new CommandEffect("emotion_rage", "Rage", 16f, (server, defender, attacker) -> {
            defender.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 12, 1, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 12, 1, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 6, 0, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 8, 0, false, true));
            message(defender, "Rage floods the mind: power rises, control frays.");
        }));

        register(new CommandEffect("emotion_fear", "Fear", 16f, (server, defender, attacker) -> {
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 10, 0, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 10, 1, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 5, 0, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 8, 0, false, true));
            message(defender, "Fear snaps your instincts awake and hollows out your resolve.");
        }));

        register(new CommandEffect("emotion_despair", "Despair", 22f, (server, defender, attacker) -> {
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 12, 2, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 12, 2, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 12, 2, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 6, 0, false, true));
            message(defender, "Despair settles over every thought.");
        }));

        register(new CommandEffect("emotion_calm", "Calm", 14f, (server, defender, attacker) -> {
            defender.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 0, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 8, 0, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 8, 0, false, true));
            message(defender, "Calm returns, steady but slower.");
        }));

        register(new CommandEffect("view_words", "Known Words", 0f, (server, defender, attacker) -> {
            if (!(attacker instanceof ServerPlayer attackerPlayer)) {
                return;
            }
            // FIXED: previously only ever checked `defender instanceof
            // ServerPlayer`, so a mob defender (Elf/Elder Elf/Human Mage/
            // Shade) always showed "no words at all" regardless of how
            // much magic it actually knew - now uses the same
            // knownWordsOf() helper "learn_word" already relies on,
            // which reads a mob's real vocabulary() too.
            List<ResourceLocation> known = new ArrayList<>(knownWordsOf(defender));
            if (known.isEmpty()) {
                attackerPlayer.sendSystemMessage(Component.literal("This mind holds no words of the Ancient Language at all."));
                return;
            }
            // "not all, but some" per direction - a glimpse, not a full dump.
            java.util.Collections.shuffle(known, RANDOM);
            List<String> words = known.stream()
                .limit(5)
                .map(id -> {
                    var word = com.dragonspeech.word.WordRegistry.get(id);
                    return word != null ? word.trueName() + " (" + word.meaning() + ")" : id.toString();
                })
                .toList();
            attackerPlayer.sendSystemMessage(Component.literal("A glimpse of what they know: " + String.join(", ", words)));
        }));

        register(new CommandEffect("sever_random_word", "Remove Word", 30f, (server, defender, attacker) -> {
            // FIXED: same issue as view_words - now uses knownWordsOf()
            // so this actually works against a mob defender, removing
            // the word from its MobVocabulary via the new unlearn()
            // method rather than only ever handling a ServerPlayer.
            List<ResourceLocation> known = new ArrayList<>(knownWordsOf(defender));
            if (known.isEmpty()) {
                if (attacker instanceof ServerPlayer attackerPlayer) {
                    attackerPlayer.sendSystemMessage(Component.literal("They know no words to remove."));
                }
                return;
            }
            ResourceLocation removed = known.get(RANDOM.nextInt(known.size()));
            var word = com.dragonspeech.word.WordRegistry.get(removed);
            String label = word != null ? word.trueName() : removed.toString();

            if (defender instanceof ServerPlayer defenderPlayer) {
                var vocabulary = com.dragonspeech.vocabulary.VocabularyAccess.get(defenderPlayer);
                com.dragonspeech.vocabulary.VocabularyAccess.set(defenderPlayer, vocabulary.withWordRemoved(removed));
                com.dragonspeech.network.VocabularySyncHooks.pushSync(defenderPlayer);
            } else if (defender instanceof com.dragonspeech.mob.casting.SpellcastingMob spellcaster) {
                spellcaster.vocabulary().unlearn(removed);
            }
            message(defender, "A word is torn loose from memory: " + label);
        }));

        /**
         * "Learn Word" - the elven-trial path's second half. Per explicit
         * direction: winning a mind duel against a Shade should let you
         * take one of ITS known words for your own, same as elves rarely
         * trading elven-trial-tier words does for the first half (see
         * MobTradeOffers.trialWordsFor). For a player defender, this does
         * the same thing "Remove Word" doesn't: sever_random_word only
         * ever strips a word from the loser, it never hands it to the
         * winner - this command is the one that actually grants it,
         * DiscoveryMethod.ELVEN_TRIAL either way (matching the "found via
         * elven trial" words in DICTIONARY.md this is explicitly meant to
         * be an alternate path into). Deliberately a SEPARATE command from
         * sever_random_word rather than folded into it, so a player
         * choosing between them is making a real choice: deny them the
         * word, or take it for yourself.
         */
        register(new CommandEffect("learn_word", "Learn Word", 30f, (server, defender, attacker) -> {
            if (!(attacker instanceof ServerPlayer attackerPlayer)) {
                return; // only a player has a vocabulary to add this to
            }

            // The rare true-name jackpot - see TRUE_NAME_BONUS_CHANCE's own
            // comment for exactly what "knows their own true name" checks
            // today. Rolled BEFORE the normal word logic below, since it's
            // framed as happening INSTEAD of a word, not alongside one -
            // even a defender with no ordinary words left to teach can
            // still trigger this.
            if (defender instanceof ServerPlayer defenderPlayer
                && MindDataAccess.get(defenderPlayer).ownName().isPresent()
                && !TrueNameService.knows(attackerPlayer, defenderPlayer)
                && RANDOM.nextFloat() < TRUE_NAME_BONUS_CHANCE) {
                TrueNameService.learn(attackerPlayer, defenderPlayer);
                attackerPlayer.sendSystemMessage(Component.literal(
                    "Instead of a mere word, something deeper surfaces from their mind - their true name "
                        + "settles into your memory. You will always know it now, unless it changes."));
                return;
            }

            List<ResourceLocation> defenderWords = knownWordsOf(defender);
            if (defenderWords.isEmpty()) {
                attackerPlayer.sendSystemMessage(Component.literal("This mind holds no words of the Ancient Language to take."));
                return;
            }
            List<ResourceLocation> candidates = defenderWords.stream()
                .filter(id -> !com.dragonspeech.vocabulary.VocabularyService.knowsWord(attackerPlayer, id))
                .toList();
            if (candidates.isEmpty()) {
                attackerPlayer.sendSystemMessage(Component.literal(
                    "You already know all of " + defender.getName().getString() + "'s words."));
                return;
            }
            ResourceLocation chosen = candidates.get(RANDOM.nextInt(candidates.size()));
            var word = com.dragonspeech.word.WordRegistry.get(chosen);
            var result = com.dragonspeech.vocabulary.VocabularyService.learnWord(
                attackerPlayer, chosen, com.dragonspeech.word.DiscoveryMethod.ELVEN_TRIAL);
            switch (result) {
                case LEARNED -> attackerPlayer.sendSystemMessage(Component.literal(
                    "Torn from their mind, a word becomes yours: \"" + (word != null ? word.trueName() : chosen) + "\""
                        + (word != null ? " - " + word.meaning() : "") + "."));
                case PREREQUISITES_NOT_MET -> attackerPlayer.sendSystemMessage(Component.literal(
                    "You glimpse \"" + (word != null ? word.trueName() : chosen)
                        + "\", but your own understanding isn't ready to hold it yet."));
                default -> {
                    // ALREADY_KNOWN/UNKNOWN_WORD shouldn't be reachable given the
                    // filtering above, but stay silent rather than risk a
                    // confusing double-message if word data changed underfoot.
                }
            }
        }));
    }

    /** Player defenders read from VocabularyAccess (their real known-words map); mob defenders (Elf/Elder Elf/Human Mage/Shade - anything implementing SpellcastingMob) read from their own vocabulary() instead. Anything else (a defender type with no vocabulary at all) has nothing to offer. */
    private static List<ResourceLocation> knownWordsOf(net.minecraft.world.entity.LivingEntity defender) {
        if (defender instanceof ServerPlayer defenderPlayer) {
            return new ArrayList<>(com.dragonspeech.vocabulary.VocabularyAccess.get(defenderPlayer).knownWords().keySet());
        }
        if (defender instanceof com.dragonspeech.mob.casting.SpellcastingMob spellcaster) {
            return new ArrayList<>(spellcaster.vocabulary().words());
        }
        return List.of();
    }

    public static void register(CommandEffect effect) {
        COMMANDS.put(effect.id(), effect);
    }

    public static Optional<CommandEffect> get(String id) {
        return Optional.ofNullable(COMMANDS.get(id));
    }

    public static Map<String, CommandEffect> all() {
        return Map.copyOf(COMMANDS);
    }

    private static void message(net.minecraft.world.entity.LivingEntity defender, String text) {
        if (defender instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal(text));
        }
    }
}
