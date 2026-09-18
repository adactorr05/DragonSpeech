package com.dragonspeech.entity;

import com.dragonspeech.command.DragonSpeechCommandRoot;
import com.dragonspeech.command.CommandPermissions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "/dragonspeech summonmob <elf|elder_elf|human_mage|shade|dragon> [pos]" -
 * "when I type /summon I could just do /summon elf... right now it says
 * there are no results unless I type dragonspeech before it" per
 * explicit direction.
 *
 * Vanilla's own "/summon" tab-completion suggests by the FULL registry
 * id (namespace:path), and a bare word like "elf" with no namespace
 * always resolves against the DEFAULT (minecraft:) namespace per
 * Minecraft's own identifier-parsing rules - "elf" alone can never mean
 * "dragonspeech:elf" there, which isn't something this mod can override
 * without replacing vanilla's summon command outright (a much bigger,
 * riskier change than it's worth for this). This is a separate,
 * dedicated command instead - same practical effect (a short name that
 * works), without touching vanilla's own command at all.
 *
 * Position defaults to the command source's own position if omitted,
 * same as vanilla "/summon" - "/dragonspeech summonmob elf" just works from
 * wherever you're standing; add a position argument only if you want to
 * place it somewhere specific.
 */
public final class DragonSpeechSummonCommand {

    private DragonSpeechSummonCommand() {}

    private static final Map<String, EntityType<?>> SHORT_NAMES = new LinkedHashMap<>();
    static {
        SHORT_NAMES.put("elf", DragonSpeechEntities.ELF);
        SHORT_NAMES.put("elder_elf", DragonSpeechEntities.ELDER_ELF);
        SHORT_NAMES.put("human_mage", DragonSpeechEntities.HUMAN_MAGE);
        SHORT_NAMES.put("shade", DragonSpeechEntities.SHADE);
        SHORT_NAMES.put("dragon", DragonSpeechEntities.DRAGON);
    }

    public static void register() {
        DragonSpeechCommandRoot.add(() -> Commands.literal("summonmob")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.argument("type", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SHORT_NAMES.keySet(), builder))
                .executes(ctx -> summonAt(ctx, ctx.getSource().getPosition()))
                .then(Commands.argument("pos", Vec3Argument.vec3())
                    .executes(ctx -> summonAt(ctx, Vec3Argument.getVec3(ctx, "pos"))))));
    }

    /**
     * VERSION-RISK NOTE: EntityType.create(ServerLevel) and Mob.
     * finalizeSpawn(ServerLevel, DifficultyInstance, MobSpawnType,
     * SpawnGroupData) are the long-stable vanilla entity-creation path
     * (the same one vanilla's own "/summon" and natural spawning both
     * use) - written from memory of that shape without a decompiled jar
     * to confirm against this session. If this doesn't compile, check
     * EntityType's exact create(...) overload first.
     */
    private static int summonAt(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        String key = StringArgumentType.getString(ctx, "type").toLowerCase(Locale.ROOT);
        EntityType<?> type = SHORT_NAMES.get(key);
        CommandSourceStack source = ctx.getSource();
        if (type == null) {
            source.sendFailure(Component.literal(
                "Unknown Dragon Speech entity \"" + key + "\" - try: " + String.join(", ", SHORT_NAMES.keySet())));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Entity entity = type.create(level);
        if (entity == null) {
            source.sendFailure(Component.literal("Failed to create " + key + "."));
            return 0;
        }
        entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.COMMAND, null);
        }
        if (!level.addFreshEntity(entity)) {
            source.sendFailure(Component.literal("Could not spawn " + key + " here."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Summoned new " + entity.getDisplayName().getString()), true);
        return 1;
    }
}
