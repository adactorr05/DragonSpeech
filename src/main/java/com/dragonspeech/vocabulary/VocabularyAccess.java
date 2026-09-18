package com.dragonspeech.vocabulary;

import net.minecraft.server.level.ServerPlayer;

public final class VocabularyAccess {

    private VocabularyAccess() {}

    public static PlayerVocabulary get(ServerPlayer player) {
        PlayerVocabulary data = player.getAttached(PlayerVocabularyAttachments.VOCABULARY);
        return data != null ? data : PlayerVocabulary.empty();
    }

    public static void set(ServerPlayer player, PlayerVocabulary vocabulary) {
        player.setAttached(PlayerVocabularyAttachments.VOCABULARY, vocabulary);
    }
}
