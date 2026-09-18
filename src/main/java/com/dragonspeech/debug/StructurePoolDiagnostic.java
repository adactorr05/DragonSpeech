package com.dragonspeech.debug;

import com.dragonspeech.DragonSpeech;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * TEMPORARY diagnostic, not a real feature - added specifically to
 * answer one question directly instead of guessing further: do our 4
 * template pools actually load with real content, or are they
 * resolving empty?
 *
 * StructureTemplatePool.getRandomTemplate() (confirmed by reading the
 * real decompiled source) can ONLY return EmptyPoolElement.INSTANCE -
 * the one and only way JigsawPlacement.addPieces() fails immediately
 * and unconditionally for a size:0 structure like all four of ours -
 * if StructureTemplatePool.size() is 0. This queries the actual
 * registry at server startup and logs that real number for each pool,
 * by name, so there's no more guessing about it either way.
 *
 * Safe to remove once this specific question is answered - this isn't
 * meant to ship long-term.
 */
public final class StructurePoolDiagnostic {

    private static final String[] POOL_NAMES = {
            "ruin_tower", "ruin_common", "ruined_tower_2", "tall_tower"
    };

    private StructurePoolDiagnostic() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var registry = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
            DragonSpeech.LOGGER.info("[DragonSpeech] === Structure pool diagnostic ===");
            for (String name : POOL_NAMES) {
                ResourceLocation id = DragonSpeech.id(name);
                ResourceKey<StructureTemplatePool> key = ResourceKey.create(Registries.TEMPLATE_POOL, id);
                StructureTemplatePool pool = registry.get(key);
                if (pool == null) {
                    DragonSpeech.LOGGER.info("[DragonSpeech]   {} -> NOT FOUND IN REGISTRY AT ALL", id);
                } else {
                    DragonSpeech.LOGGER.info("[DragonSpeech]   {} -> loaded, size()={}", id, pool.size());
                }
            }
            DragonSpeech.LOGGER.info("[DragonSpeech] === end diagnostic ===");
        });
    }
}