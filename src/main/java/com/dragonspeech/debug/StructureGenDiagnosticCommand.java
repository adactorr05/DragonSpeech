package com.dragonspeech.debug;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.command.CommandPermissions;
import com.dragonspeech.command.DragonSpeechCommandRoot;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Optional;

/**
 * TEMPORARY diagnostic - "/dragonspeech diag structure <name>". Calls
 * the EXACT same real vanilla methods PlaceCommand.placeStructure does
 * (confirmed by reading its actual decompiled source), but logs the
 * intermediate result of findValidGenerationPoint SEPARATELY from the
 * final isValid() check - the one piece of information not visible
 * from outside the command itself, since PlaceCommand only ever reports
 * the final yes/no.
 *
 * If findValidGenerationPoint comes back EMPTY, the failure is
 * somewhere in the pool/piece/biome layer (JigsawPlacement,
 * SinglePoolElement, etc.) despite everything read so far suggesting
 * it should succeed. If it comes back PRESENT but the piece count is 0
 * or isValid() still fails afterward, the failure is specifically in
 * how the pieces get built into a PiecesContainer - a layer not yet
 * examined at all.
 *
 * Safe to remove once this question is answered.
 */
public final class StructureGenDiagnosticCommand {

    private StructureGenDiagnosticCommand() {}

    public static void register() {
        DragonSpeechCommandRoot.add(() -> Commands.literal("diag")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.literal("structure")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))));
    }

    private static int run(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        ResourceLocation id = DragonSpeech.id(name);
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);

        if (structure == null) {
            source.sendFailure(Component.literal("[diag] " + id + " -> not found in Structure registry at all"));
            return 0;
        }

        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();

        // Same predicate PlaceCommand itself uses - (holder) -> true - so this matches /place exactly, not a stricter test.
        Structure.GenerationContext genContext = new Structure.GenerationContext(
                source.registryAccess(),
                chunkGenerator,
                chunkGenerator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                level.getSeed(),
                chunkPos,
                level,
                (Holder<Biome> holder) -> true
        );

        Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(genContext);
        source.sendSuccess(() -> Component.literal("[diag] findValidGenerationPoint present: " + stub.isPresent()), false);

        if (stub.isPresent()) {
            int pieceCount = stub.get().getPiecesBuilder().build().pieces().size();
            source.sendSuccess(() -> Component.literal("[diag] resulting piece count: " + pieceCount), false);
        }

        // Now the full real call, exactly like PlaceCommand does, for direct comparison against the above.
        StructureStart structureStart = structure.generate(
                source.registryAccess(), chunkGenerator, chunkGenerator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                chunkPos, 0, level, (Holder<Biome> holder) -> true
        );
        source.sendSuccess(() -> Component.literal("[diag] final structureStart.isValid(): " + structureStart.isValid()), false);

        return 1;
    }
}
