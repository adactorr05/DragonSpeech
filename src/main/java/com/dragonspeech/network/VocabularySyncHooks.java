package com.dragonspeech.network;

import com.dragonspeech.event.DragonSpeechEvents;
import com.dragonspeech.vocabulary.VocabularyService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps each client's KnownWordsClientCache fresh by pushing a sync packet
 * whenever it might be stale: on login, whenever that specific player
 * learns a new word, and whenever they toggle a favorite. The casting
 * grid and grimoire screens just read from the client cache and never
 * have to request anything on open.
 */
public final class VocabularySyncHooks {

    private VocabularySyncHooks() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            pushSync(handler.getPlayer());
            com.dragonspeech.ward.WardService.pushSync(handler.getPlayer());
        });

        DragonSpeechEvents.WORD_DISCOVERED.register((player, word, method) -> pushSync(player));
    }

    /** Public so the favorite-toggle handler can request a fresh push after changing state - the client cache should never drift from server truth. */
    public static void pushSync(ServerPlayer player) {
        JsonArray array = new JsonArray();
        for (VocabularyService.KnownWordView view : VocabularyService.getKnownWordViews(player)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", view.id().toString());
            obj.addProperty("true_name", view.word().trueName());
            obj.addProperty("meaning", view.word().meaning());
            obj.addProperty("category", view.word().category().getSerializedName());
            obj.addProperty("domain", view.word().domain().getSerializedName());
            obj.addProperty("precision", view.word().precision());
            obj.addProperty("favorited", view.entry().favorited());
            obj.addProperty("discovery_method", view.entry().learnedVia().getSerializedName());
            view.word().effectHandlerId().ifPresent(id -> obj.addProperty("effect_handler", id.toString()));
            array.add(obj);
        }

        DragonSpeechNetworking.sendKnownWordsSync(player, array.toString());
    }
}
