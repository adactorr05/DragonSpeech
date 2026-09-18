package com.dragonspeech.mixin;

import com.dragonspeech.config.DragonSpeechConfig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * "Structure generation should be affected by Magic Difficulty" per
 * explicit direction - see DragonSpeechConfig.structureSpacingMultiplier
 * for the actual EASY/NORMAL/HARD numbers.
 *
 * REWRITTEN after reading RandomSpreadStructurePlacement's real
 * decompiled source (thank you for grabbing it before applying the
 * first version). getPotentialStructureChunk - the method that actually
 * computes the placement grid - reads the private `spacing` FIELD
 * directly, not the spacing() getter. A mixin on the getter alone would
 * have compiled fine and done nothing to real generation, since field
 * access from within the same class bypasses getters entirely. This
 * version injects at the head of getPotentialStructureChunk itself and
 * replicates its exact logic with an adjusted spacing value, rather
 * than trying to intercept the field reads directly.
 *
 * Still scoped ONLY to our own 4 structure sets via salt() (inherited
 * from StructurePlacement, confirmed still present via
 * setLargeFeatureWithSalt(l, k, m, this.salt()) in the real source) -
 * every other mod's and vanilla's structures are completely untouched.
 *
 * Defensively clamps the adjusted spacing to stay above separation+1 -
 * the original class enforces spacing > separation at parse time on the
 * UNADJUSTED values only (RandomSpreadStructurePlacement::validate), so
 * an EASY-mode reduction could theoretically violate that at runtime if
 * spacing and separation were ever configured very close together. Not
 * currently a risk with our actual values, but cheap insurance.
 *
 * VERSION-RISK NOTE: spreadType().evaluate(WorldgenRandom, int) is
 * called exactly as the real decompiled source calls it internally
 * (this.spreadType.evaluate(worldgenRandom, n)) - reasonably confident,
 * but RandomSpreadType's own visibility/signature wasn't independently
 * verified the way RandomSpreadStructurePlacement itself now has been.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {

    private static final Set<Integer> DRAGONSPEECH_SALTS = Set.of(918273, 348905, 512094, 736201);

    @Inject(method = "getPotentialStructureChunk", at = @At("HEAD"), cancellable = true)
    private void dragonspeech$applyDifficultyMultiplier(long seed, int i, int j, CallbackInfoReturnable<ChunkPos> cir) {
        RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
        int salt = ((StructurePlacementSaltAccessor) (Object) this).dragonspeech$salt();
        if (!DRAGONSPEECH_SALTS.contains(salt)) {
            return; // not one of ours - real vanilla method runs unmodified
        }

        int baseSpacing = self.spacing();
        int separation = self.separation();
        int adjustedSpacing = Math.round(baseSpacing * DragonSpeechConfig.structureSpacingMultiplier());
        adjustedSpacing = Math.max(adjustedSpacing, separation + 1); // never let spacing drop to/below separation

        int k = Math.floorDiv(i, adjustedSpacing);
        int m = Math.floorDiv(j, adjustedSpacing);
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setLargeFeatureWithSalt(seed, k, m, salt);
        int n = adjustedSpacing - separation;
        int o = self.spreadType().evaluate(worldgenRandom, n);
        int p = self.spreadType().evaluate(worldgenRandom, n);

        cir.setReturnValue(new ChunkPos(k * adjustedSpacing + o, m * adjustedSpacing + p));
    }
}