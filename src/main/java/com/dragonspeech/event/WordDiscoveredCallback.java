package com.dragonspeech.event;

import com.dragonspeech.word.DiscoveryMethod;
import com.dragonspeech.word.Word;
import net.minecraft.server.level.ServerPlayer;

public interface WordDiscoveredCallback {
    void onWordDiscovered(ServerPlayer player, Word word, DiscoveryMethod method);
}
