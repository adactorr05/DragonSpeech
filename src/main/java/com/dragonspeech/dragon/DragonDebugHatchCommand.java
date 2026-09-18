package com.dragonspeech.dragon;

import com.dragonspeech.command.DragonSpeechCommandRoot;
import com.dragonspeech.command.CommandPermissions;
import com.dragonspeech.dragon.breed.DragonBreed;
import com.dragonspeech.dragon.egg.DragonEggHatchingBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "/dragonspeech debug hatch" - "since eggs will take a while to hatch
 * instead of the right click we used to have, add a debug command that
 * will hatch the egg I am looking [at]" per explicit direction.
 *
 * This remains a separate implementation file because hatch is a one-shot
 * action rather than a boolean toggle, but it contributes its "debug hatch"
 * child to DragonSpeechCommandRoot. The mod now has one /dragonspeech root
 * registration instead of relying on Brigadier to merge duplicate roots.
 *
 * Bypasses the actual wait entirely (habitat checks, random-tick
 * rolls, stage progression) - calls VariantDragonEggBlock's own
 * hatch() directly, the exact same method the real random-tick path
 * eventually calls on success, so a debug-forced hatch produces an
 * identical result to a "real" one (same bonding roll, same wild-vs-
 * bonded outcome) - just skipping the timing, not faking the result.
 */
public final class DragonDebugHatchCommand {
    private DragonDebugHatchCommand() {}

    private static final double REACH = 6.0;

    public static void register() {
        DragonSpeechCommandRoot.add(() -> Commands.literal("debug")
            .requires(CommandPermissions::canUseAdmin)
            .then(Commands.literal("hatch")
                .executes(ctx -> hatch(ctx.getSource().getPlayerOrException()))));
    }

    private static int hatch(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0f).scale(REACH));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendSystemMessage(Component.literal("Not looking at a placed dragon egg."));
            return 0;
        }

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockState(pos).getBlock() instanceof VariantDragonEggBlock eggBlock)) {
            player.sendSystemMessage(Component.literal("Not looking at a placed dragon egg."));
            return 0;
        }

        if (!(level.getBlockEntity(pos) instanceof DragonEggHatchingBlockEntity data)) {
            player.sendSystemMessage(Component.literal("That egg has no hatching data - was it placed by /setblock without a breed set?"));
            return 0;
        }

        DragonBreed breed = data.getBreed();
        if (breed == null) {
            player.sendSystemMessage(Component.literal("That egg's breed can't be resolved (missing addon mod?) - can't hatch it."));
            return 0;
        }

        // If nobody has right-clicked it yet, credit the debug-user as
        // the placer so bonding still has someone to check compatibility
        // against, rather than always forcing a wild hatch just because
        // hatching was never manually started.
        if (data.placedBy().isEmpty()) {
            data.setPlacedBy(player.getUUID());
        }

        eggBlock.hatch(level, pos, data, breed);
        player.sendSystemMessage(Component.literal("Forced hatch."));
        return 1;
    }
}
