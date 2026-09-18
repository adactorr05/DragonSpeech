package com.dragonspeech.scar;

import com.dragonspeech.network.ScarSyncHooks;
import com.dragonspeech.network.VocabularySyncHooks;
import com.dragonspeech.vocabulary.PlayerVocabulary;
import com.dragonspeech.vocabulary.VocabularyAccess;
import com.dragonspeech.word.WordRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Picks and applies ONE scar every time a crossing happens, and always
 * tells the player exactly what it was - see ActiveScar.describe(). Every
 * scar past WORD_LOSS is a genuinely lasting handicap; the weighting
 * below keeps the common case common and the severe ones rare.
 */
public final class ScarService {

    private static final Random RANDOM = new Random();

    private record Weighted(ScarType type, int weight) {}

    private static final List<Weighted> POOL = List.of(
        new Weighted(ScarType.WORD_LOSS, 10),
        new Weighted(ScarType.CHRONIC_PAIN, 3),
        new Weighted(ScarType.REGEN_CAP, 3),
        new Weighted(ScarType.COST_CEILING, 2),
        new Weighted(ScarType.CURSED_WORD, 2),
        new Weighted(ScarType.REGEN_DISABLED, 1) // the severe end - rare on purpose
    );

    private ScarService() {}

    public static void applyRandomScar(ServerPlayer revived) {
        ScarType type = rollType();
        ActiveScar scar = build(revived, type);
        if (scar == null) {
            return; // e.g. WORD_LOSS rolled but the player knows no words yet - nothing to take
        }

        ScarAccess.set(revived, ScarAccess.get(revived).withAdded(scar));
        ScarSyncHooks.pushSync(revived);

        revived.sendSystemMessage(Component.literal("The crossing leaves its mark: " + scar.describe()));
    }

    private static ScarType rollType() {
        int total = POOL.stream().mapToInt(Weighted::weight).sum();
        int roll = RANDOM.nextInt(total);
        for (Weighted w : POOL) {
            if (roll < w.weight()) {
                return w.type();
            }
            roll -= w.weight();
        }
        return ScarType.WORD_LOSS;
    }

    private static ActiveScar build(ServerPlayer player, ScarType type) {
        return switch (type) {
            case WORD_LOSS -> buildWordLoss(player);
            case CHRONIC_PAIN -> new ActiveScar(ScarType.CHRONIC_PAIN, 0f, Optional.empty(), List.of());
            case COST_CEILING -> new ActiveScar(ScarType.COST_CEILING, 120f + RANDOM.nextInt(80), Optional.empty(), List.of());
            case REGEN_CAP -> new ActiveScar(ScarType.REGEN_CAP, 0.3f + RANDOM.nextFloat() * 0.4f, Optional.empty(), List.of());
            case REGEN_DISABLED -> new ActiveScar(ScarType.REGEN_DISABLED, 0f, Optional.empty(), List.of());
            case CURSED_WORD -> buildCursedWord(player);
        };
    }

    private static ActiveScar buildWordLoss(ServerPlayer player) {
        PlayerVocabulary vocabulary = VocabularyAccess.get(player);
        List<ResourceLocation> known = new ArrayList<>(vocabulary.knownWords().keySet());
        if (known.isEmpty()) {
            return null;
        }

        int severed = 1 + RANDOM.nextInt(Math.min(3, known.size()));
        List<ResourceLocation> lost = new ArrayList<>();
        for (int i = 0; i < severed && !known.isEmpty(); i++) {
            ResourceLocation id = known.remove(RANDOM.nextInt(known.size()));
            lost.add(id);
            vocabulary = vocabulary.withWordRemoved(id);
        }

        VocabularyAccess.set(player, vocabulary);
        VocabularySyncHooks.pushSync(player);
        return new ActiveScar(ScarType.WORD_LOSS, 0f, Optional.empty(), List.copyOf(lost));
    }

    private static ActiveScar buildCursedWord(ServerPlayer player) {
        List<ResourceLocation> known = new ArrayList<>(VocabularyAccess.get(player).knownWords().keySet());
        if (known.isEmpty()) {
            // No known words to curse - fall back to a harmless word-loss-free scar rather than nothing.
            return new ActiveScar(ScarType.CHRONIC_PAIN, 0f, Optional.empty(), List.of());
        }
        ResourceLocation cursed = known.get(RANDOM.nextInt(known.size()));
        return new ActiveScar(ScarType.CURSED_WORD, 0f, Optional.of(cursed), List.of());
    }
}
