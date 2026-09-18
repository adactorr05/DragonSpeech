package com.dragonspeech.mind;

import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The two actual actions available on the new post-victory guessing
 * screen: attempt a letter (cast one of the 5 mind-words), or submit a
 * full assembled guess. Both require the player to already have an
 * unlocked TrueNameProgress entry for that target (i.e. they've won a
 * duel against them - see TrueNameDuelHooks) - this service never
 * unlocks anything itself, only progresses what's already unlocked.
 */
public final class TrueNameProgressService {

    private static final float LETTER_ATTEMPT_STAMINA_COST = 3f;
    private static final float LETTER_SUCCESS_CHANCE = 0.55f;
    private static final long WRONG_GUESS_COOLDOWN_TICKS = 20L * 20;

    private static final Random RANDOM = new Random();
    private static final Map<UUID, Map<UUID, Long>> WRONG_GUESS_COOLDOWNS = new HashMap<>();

    private TrueNameProgressService() {}

    public enum LetterResult { NOT_UNLOCKED, ALREADY_SOLVED, TARGET_UNAVAILABLE, INSUFFICIENT_STAMINA, FAILED, GAINED_LETTER, ALREADY_HAD_LETTER }

    public record LetterOutcome(LetterResult result, char letter) {
        static LetterOutcome of(LetterResult result) {
            return new LetterOutcome(result, '\0');
        }
    }

    public static LetterOutcome attemptLetter(MinecraftServer server, ServerPlayer player, UUID targetId) {
        PlayerMindData data = MindDataAccess.get(player);
        Optional<TrueNameProgress> progress = data.progressFor(targetId);
        if (progress.isEmpty()) {
            return LetterOutcome.of(LetterResult.NOT_UNLOCKED);
        }
        if (progress.get().solved()) {
            return LetterOutcome.of(LetterResult.ALREADY_SOLVED);
        }

        Entity targetEntity = EntityLookup.byUUID(server, targetId);
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
            return LetterOutcome.of(LetterResult.TARGET_UNAVAILABLE);
        }

        PlayerMagicData magic = StaminaAccess.get(player);
        if (magic.stamina() < LETTER_ATTEMPT_STAMINA_COST) {
            return LetterOutcome.of(LetterResult.INSUFFICIENT_STAMINA);
        }
        StaminaAccess.set(player, magic.withStamina(magic.stamina() - LETTER_ATTEMPT_STAMINA_COST));

        if (RANDOM.nextFloat() > LETTER_SUCCESS_CHANCE) {
            return LetterOutcome.of(LetterResult.FAILED);
        }

        TrueName trueName = TrueNameService.getOrCreateForEntity(target);
        String plaintext = trueName.plaintext();
        if (plaintext.isEmpty()) {
            return LetterOutcome.of(LetterResult.FAILED);
        }
        char letter = plaintext.charAt(RANDOM.nextInt(plaintext.length()));

        boolean alreadyHad = progress.get().collectedLetters().indexOf(letter) >= 0;

        TrueNameProgress updated = progress.get().withLetterAdded(letter);
        MindDataAccess.set(player, data.withTrueNameProgress(updated));
        sync(server, player);

        return new LetterOutcome(alreadyHad ? LetterResult.ALREADY_HAD_LETTER : LetterResult.GAINED_LETTER, letter);
    }

    public enum GuessResult { NOT_UNLOCKED, ALREADY_SOLVED, TARGET_UNAVAILABLE, ON_COOLDOWN, CORRECT, WRONG }

    public static GuessResult submitGuess(MinecraftServer server, ServerPlayer player, UUID targetId, String guess) {
        PlayerMindData data = MindDataAccess.get(player);
        Optional<TrueNameProgress> progress = data.progressFor(targetId);
        if (progress.isEmpty()) {
            return GuessResult.NOT_UNLOCKED;
        }
        if (progress.get().solved()) {
            return GuessResult.ALREADY_SOLVED;
        }

        long now = server.overworld().getGameTime();
        Map<UUID, Long> playerCooldowns = WRONG_GUESS_COOLDOWNS.getOrDefault(player.getUUID(), Map.of());
        Long cooldownUntil = playerCooldowns.get(targetId);
        if (cooldownUntil != null && now < cooldownUntil) {
            return GuessResult.ON_COOLDOWN;
        }

        Entity targetEntity = EntityLookup.byUUID(server, targetId);
        if (!(targetEntity instanceof LivingEntity target)) {
            return GuessResult.TARGET_UNAVAILABLE;
        }

        if (TrueNameService.guessCorrectForEntity(target, guess)) {
            TrueNameService.learnForEntity(player, target);
            MindDataAccess.set(player, MindDataAccess.get(player).withTrueNameProgress(progress.get().markSolved()));
            sync(server, player);
            return GuessResult.CORRECT;
        }

        WRONG_GUESS_COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(targetId, now + WRONG_GUESS_COOLDOWN_TICKS);
        return GuessResult.WRONG;
    }

    /**
     * Pushes this player's own full progress list to their client -
     * called after every mutation (unlock, letter gained, solved) rather
     * than waiting on a periodic tick, since these are all
     * player-initiated actions that expect an immediate visible result.
     * Entity display names are resolved best-effort (an offline/unloaded
     * target just shows as "Unknown" - display-only, doesn't affect
     * whether guessing/attempting still works).
     */
    public static void sync(MinecraftServer server, ServerPlayer player) {
        PlayerMindData data = MindDataAccess.get(player);
        com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
        for (TrueNameProgress progress : data.trueNameProgress().values()) {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("target_id", progress.targetId().toString());
            Entity targetEntity = EntityLookup.byUUID(server, progress.targetId());
            obj.addProperty("target_name", targetEntity != null ? targetEntity.getName().getString() : "Unknown");
            obj.addProperty("collected_letters", progress.collectedLetters());
            obj.addProperty("solved", progress.solved());
            if (progress.solved() && targetEntity instanceof LivingEntity living) {
                obj.addProperty("solved_name", TrueNameService.getOrCreateForEntity(living).plaintext());
            }
            entries.add(obj);
        }
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.add("entries", entries);
        com.dragonspeech.network.DragonSpeechNetworking.sendTrueNameProgressSync(player, root.toString());
    }
}
