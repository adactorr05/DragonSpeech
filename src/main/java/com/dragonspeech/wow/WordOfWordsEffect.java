package com.dragonspeech.wow;

import net.minecraft.server.level.ServerPlayer;

/** Entry point kept for compatibility with older call sites. */
public final class WordOfWordsEffect {
    private WordOfWordsEffect() {}

    public static void register() {
        WordOfWordsService.register();
        WordOfWordsHaltManager.register();
    }

    /** Speaking the Word itself is free; the GUI actions are what consume stamina. */
    public static void attempt(ServerPlayer player) {
        WordOfWordsService.open(player);
    }
}
