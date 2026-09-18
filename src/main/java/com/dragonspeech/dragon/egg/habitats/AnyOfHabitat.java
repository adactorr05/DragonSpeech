package com.dragonspeech.dragon.egg.habitats;

import com.dragonspeech.dragon.egg.Habitat;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * New habitat type, not in Dragon Mounts Legacy's original 7 - their
 * own PickyHabitat only expresses "ALL nested conditions must be true"
 * (any one scoring zero fails the whole thing). Void Dragon's "anywhere
 * in the End, OR near bedrock in the Overworld/Nether" and End
 * Dragon's "near endstone OR in the End" both genuinely need "any ONE
 * of these being true is enough" instead - the opposite logic,
 * structured the same way (a list of nested Habitats) so it composes
 * naturally alongside Picky if a future breed ever needs both AND and
 * OR combined.
 */
public record AnyOfHabitat(List<Habitat> habitats) implements Habitat {
    public static final String TYPE = "any_of";
    public static final MapCodec<AnyOfHabitat> CODEC = Habitat.CODEC
            .listOf()
            .fieldOf("any_of")
            .xmap(AnyOfHabitat::new, AnyOfHabitat::habitats);

    static {
        Habitat.register(TYPE, CODEC);
    }

    @Override
    public int getHabitatPoints(Level level, BlockPos pos) {
        // Highest-scoring satisfied condition wins, rather than
        // summing every match - matches Picky's own "points reflect
        // how well THIS specific rule is satisfied" spirit rather than
        // rewarding an egg for happening to be in two OR-branches at
        // once (e.g. somehow both "in the End" and "near bedrock"
        // simultaneously shouldn't double-count).
        int best = 0;
        for (var habitat : habitats) {
            best = Math.max(best, habitat.getHabitatPoints(level, pos));
        }
        return best;
    }

    @Override
    public String typeId() {
        return TYPE;
    }
}
