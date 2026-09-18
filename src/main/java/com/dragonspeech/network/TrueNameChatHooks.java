package com.dragonspeech.network;

import com.dragonspeech.mind.ContactResolver;
import com.dragonspeech.mind.TrueNameService;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/**
 * Speaking a true name in chat - mirrors ChatCastHooks exactly (same
 * "hidden from everyone but the speaker, replaced with gibberish"
 * treatment a spell utterance gets), but for true names instead of
 * spell words: type a single word matching some living thing's true
 * name, anywhere on the server, no need to be nearby.
 *
 * On a match: the name is added to the speaker's grimoire
 * (PlayerMindData.learnedNames, via TrueNameService.learnForEntity) if
 * it wasn't already known, then a connection opens immediately,
 * skipping Contact's travel time and the whole barrier fight - see
 * ContactResolver.instantConnectViaTrueName(). This is true whether the
 * name was JUST learned this message or was already in the grimoire -
 * per the design, knowing a true name makes the whole contest moot every
 * time, not just the first.
 *
 * Only single-word messages are checked (true names are one word) - this
 * keeps normal sentences from ever being scanned entity-by-entity.
 *
 * VERSION-RISK NOTE: ServerLevel#getAllEntities() is this file's one
 * newly-touched API beyond what ChatCastHooks already proves works -
 * if it doesn't resolve, check ServerLevel/PersistentEntitySectionManager
 * in your decompiled sources for the current "every loaded entity in
 * this level" accessor.
 */
public final class TrueNameChatHooks {

    private TrueNameChatHooks() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String raw = message.signedContent().trim();
            if (raw.isBlank() || raw.contains(" ")) {
                return true; // true names are a single word - anything with spaces is ordinary chat, untouched
            }

            LivingEntity match = findEntityByTrueName(sender, raw);
            if (match == null) {
                return true; // not a true name - let it through
            }

            broadcastGibberish(sender, raw);
            MinecraftServer server = sender.getServer();
            if (server != null) {
                server.execute(() -> processTrueNameSpoken(server, sender, match));
            }
            return false; // suppress the original message - no one else ever sees what was typed
        });
    }

    private static void processTrueNameSpoken(MinecraftServer server, ServerPlayer speaker, LivingEntity target) {
        boolean alreadyKnown = TrueNameService.knowsEntity(speaker, target);
        if (!alreadyKnown) {
            TrueNameService.learnForEntity(speaker, target);
            speaker.sendSystemMessage(Component.literal("The name settles into your memory - you will always know it now, unless it changes."));
        }
        ContactResolver.instantConnectViaTrueName(server, speaker, target);
    }

    /** Checks all online players, then every loaded entity across every level - bounded and only ever run when a player actually sends a chat message, not per-tick. */
    private static LivingEntity findEntityByTrueName(ServerPlayer speaker, String guess) {
        MinecraftServer server = speaker.getServer();
        if (server == null) {
            return null;
        }
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate == speaker) {
                continue;
            }
            if (TrueNameService.guessCorrectForEntity(candidate, guess)) {
                return candidate;
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && !(living instanceof ServerPlayer) && living.isAlive()) {
                    if (TrueNameService.guessCorrectForEntity(living, guess)) {
                        return living;
                    }
                }
            }
        }
        return null;
    }

    /** Same treatment ChatCastHooks gives a spell utterance - everyone but the speaker sees same-shaped gibberish. */
    private static void broadcastGibberish(ServerPlayer speaker, String token) {
        MinecraftServer server = speaker.getServer();
        if (server == null) {
            return;
        }
        Random random = new Random();
        StringBuilder gibberish = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            gibberish.append((char) ('a' + random.nextInt(26)));
        }

        String speakerName = speaker.getGameProfile().getName();
        Component garbled = Component.literal("<" + speakerName + "> " + gibberish);
        Component clear = Component.literal("You speak the name: " + token);

        for (ServerPlayer listener : server.getPlayerList().getPlayers()) {
            if (listener.getUUID().equals(speaker.getUUID())) {
                listener.sendSystemMessage(clear);
            } else {
                listener.sendSystemMessage(garbled);
            }
        }
    }
}
