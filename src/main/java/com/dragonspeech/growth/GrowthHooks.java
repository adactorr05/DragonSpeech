package com.dragonspeech.growth;

import com.dragonspeech.event.DragonSpeechEvents;

public final class GrowthHooks {

    private GrowthHooks() {}

    public static void register() {
        DragonSpeechEvents.WORD_DISCOVERED.register((player, word, method) -> StaminaMilestones.onWordDiscovered(player));
    }
}
