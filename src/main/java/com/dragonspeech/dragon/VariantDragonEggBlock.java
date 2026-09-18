package com.dragonspeech.dragon;

import com.dragonspeech.dragon.breed.DragonBreed;
import com.dragonspeech.dragon.egg.DragonEggHatchingBlockEntities;
import com.dragonspeech.dragon.egg.DragonEggHatchingBlockEntity;
import com.dragonspeech.dragon.egg.Habitat;
import com.dragonspeech.mind.EntityLookup;
import com.dragonspeech.storage.DragonSpeechComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * The placed-block form of one of the 7 dragon eggs, now backed by
 * DragonEggHatchingBlockEntity for the actual hatching process.
 *
 * "I do like their hatching system so I will use that" per explicit
 * direction - the STAGE-PROGRESSION CONCEPT here (right-click starts
 * it, random-tick rolls advance it through crack stages toward a final
 * hatch) is adapted from Dragon Mounts Legacy's own HatchableEggBlock
 * (GPL-3.0). See DragonEggHatchingBlockEntity's own doc for why the
 * actual role habitat points play here (gating/scaling THIS egg's own
 * known-breed hatch roll) is a genuine redesign, not what their code
 * does with habitat scores.
 *
 * INTERACTION REDESIGNED to resolve a real conflict: this project's
 * own earlier direction was "right-click = instant pickup" (recovered
 * from an uploaded backup, before the Dragon Mounts Legacy pivot);
 * this new hatching mechanic fundamentally needs right-click to START
 * hatching instead (matching Dragon Mounts Legacy's own trigger).
 * Resolved with a sneak-modifier split: SNEAK+right-click still
 * instant-picks-up (only while not yet hatching - same as before, just
 * gated), plain right-click starts hatching if not already started.
 * Once hatching has begun, neither picks it back up - matches how
 * Dragon Mounts Legacy's own placed egg becomes commitment once
 * started.
 */
public class VariantDragonEggBlock extends DragonEggBlock implements EntityBlock {

    private final DragonColor color;

    public VariantDragonEggBlock(DragonColor color, BlockBehaviour.Properties properties) {
        super(properties);
        this.color = color;
    }

    public DragonColor getColor() {
        return color;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // FIX: "the debug hatch command always says 'breed can't be
        // resolved,' no matter which egg color" per explicit direction
        // - real bug, confirmed directly: this method never called
        // setBreedId() at all, so the block entity's breed was always
        // null regardless of this block's own actual color. Same
        // color-to-breed mapping already confirmed for
        // DragonEntity.color() (RED->fire, BRONZE->gold, BLUE->lightning,
        // WHITE->ice, GREEN->forest, BLACK->void, ENDER->end), just the
        // reverse direction.
        DragonEggHatchingBlockEntity entity = new DragonEggHatchingBlockEntity(DragonEggHatchingBlockEntities.HATCHING_EGG, pos, state);
        entity.setBreedId(breedIdForColor(color));
        return entity;
    }

    /**
     * FIX: "right now, it still won't bond" per explicit direction -
     * the real root cause, confirmed directly: this override didn't
     * exist at all. The block entity's seed and never-bondable flag
     * were never transferred from the placed item's own data at all,
     * silently defaulting to 0L/false instead. That's not just "wrong
     * data" - it means the hatch-time compatibility check
     * (EggCompatibility.isCompatible(player, data.seed())) was rolling
     * against a completely different seed than the one checkEgg()
     * actually showed the player when they checked the egg by hand -
     * an effectively unrelated, essentially random result instead of
     * the same deterministic roll. This single gap explains both
     * reported symptoms at once: natural hatching not bonding when it
     * said it could, and the wrong "answers to no one" message showing
     * even for eggs that should have bonded - both come from hatch()
     * checking compatibility against the wrong data, not from any bug
     * in the bonding logic or messaging logic themselves.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof DragonEggHatchingBlockEntity data) {
            Long seed = stack.get(com.dragonspeech.storage.DragonSpeechComponents.EGG_SEED);
            Boolean neverBondable = stack.get(com.dragonspeech.storage.DragonSpeechComponents.EGG_NEVER_BONDABLE);
            if (seed == null) {
                // Placed without ever being right-clicked first (skipping
                // checkEgg(), which is what normally generates this) -
                // same generation approach as DragonEggBlockItem's own
                // ensureSeeded(), not a fixed fallback value, so this
                // edge case still gets a genuinely random compatibility
                // roll rather than a silently predictable one.
                long fresh = new java.util.Random().nextLong();
                data.setSeed(fresh);
                data.setNeverBondable(EggCompatibility.rollNeverBondable(fresh));
            } else {
                data.setSeed(seed);
                data.setNeverBondable(Boolean.TRUE.equals(neverBondable));
            }
        }
    }

    private static net.minecraft.resources.ResourceLocation breedIdForColor(DragonColor color) {
        String breedPath = switch (color) {
            case RED -> "fire";
            case BRONZE -> "gold";
            case BLUE -> "lightning";
            case WHITE -> "ice";
            case GREEN -> "forest";
            case BLACK -> "void";
            case ENDER -> "end";
        };
        return com.dragonspeech.DragonSpeech.id(breedPath);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof DragonEggHatchingBlockEntity data)) {
            return InteractionResult.PASS;
        }

        if (data.isHatching()) {
            return InteractionResult.PASS; // committed - matches Dragon Mounts Legacy's own "already hatching, do nothing" behavior
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            // Sneak+right-click, not yet hatching - preserves the
            // original "instant pickup" behavior, just gated to before
            // commitment.
            ItemStack egg = new ItemStack(this);
            egg.set(DragonSpeechComponents.EGG_COLOR, color.getSerializedName());
            egg.set(DragonSpeechComponents.EGG_SEED, data.seed());
            egg.set(DragonSpeechComponents.EGG_NEVER_BONDABLE, data.isNeverBondable());
            if (!player.getInventory().add(egg)) {
                player.drop(egg, false);
            }
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.CONSUME;
        }

        // Plain right-click, not yet hatching - starts the process.
        if (data.placedBy().isEmpty() && player instanceof ServerPlayer sp) {
            data.setPlacedBy(sp.getUUID());
        }
        data.beginHatching();
        level.setBlock(pos, state, Block.UPDATE_ALL); // trigger isRandomlyTicking to be re-checked
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.85f, 1.0f);
        return InteractionResult.CONSUME;
    }

    /** No teleport-on-attack - see original class doc, unchanged from before this rewrite. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        // Deliberately empty.
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0f;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true; // gate is on the block ENTITY's hatching flag, checked inside randomTick itself - isRandomlyTicking can't see the block entity
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof DragonEggHatchingBlockEntity data) || !data.isHatching()) {
            return;
        }

        DragonBreed breed = data.getBreed();
        if (breed == null) {
            return;
        }

        // FIX (real redesign, not a port - see DragonEggHatchingBlockEntity's
        // own doc): habitat points gate/scale THIS egg's own known-breed
        // hatch roll, rather than deciding which breed it becomes.
        int points = 0;
        for (Habitat habitat : breed.habitats()) {
            points += habitat.getHabitatPoints(level, pos);
        }

        boolean gated = !breed.habitats().isEmpty();
        if (gated && points < DragonEggHatchingBlockEntity.MIN_HABITAT_POINTS) {
            return; // conditions not met yet - no progress this tick, but no penalty either
        }

        // Config GUI (Server tab) "Egg Hatch Speed Multiplier" - a straight multiplier on the final
        // per-tick roll chance, so 2.0 hatches roughly twice as fast on average, 0.5 roughly half as fast.
        float effectiveChance = breed.hatchChance() * (gated ? (1f + points * 0.15f) : 1f)
                * com.dragonspeech.config.DragonSpeechConfig.eggHatchSpeedMultiplier();

        if (random.nextFloat() < effectiveChance) {
            if (data.isFinalStage()) {
                hatch(level, pos, data, breed);
            } else {
                level.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.85f, 0.95f + random.nextFloat() * 0.2f);
                data.advanceStage();
            }
        }
    }

    /**
     * The actual spawn - "they can only be bonded when you hatch the
     * egg... if there are dragons that spawn, they are wild dragons and
     * cannot be tamed" per explicit direction. Compatibility (the
     * separate never-bondable/attunement-roll check, kept per your
     * direction to run alongside habitat timing) decides whether THIS
     * hatch bonds to whoever placed it, or emerges wild - it does not
     * decide WHETHER it hatches at all, only what state it hatches
     * into.
     */
    /** Package-visible (not private) specifically so DragonDebugHatchCommand can force this directly, skipping the stage-progression/random-roll wait - "since eggs will take a while to hatch instead of the right click we used to have, add a debug command" per explicit direction. */
    void hatch(ServerLevel level, BlockPos pos, DragonEggHatchingBlockEntity data, DragonBreed breed) {
        ResourceLocation breedId = data.getBreedId();
        level.removeBlock(pos, false);

        DragonEntity dragon = com.dragonspeech.entity.DragonSpeechEntities.DRAGON.create(level);
        if (dragon == null) {
            return;
        }
        dragon.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        dragon.setBreedId(breedId);
        dragon.setBaby(true);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.7f);

        UUID placedByUuid = data.placedBy().orElse(null);
        ServerPlayer placer = placedByUuid != null ? level.getServer().getPlayerList().getPlayer(placedByUuid) : null;

        boolean bonded = false;
        if (!data.isNeverBondable() && placer != null && EggCompatibility.isCompatible(placer, data.seed())) {
            PlayerBondData bondData = PlayerBondAccess.get(placer);
            boolean alreadyBonded = bondData.currentBondedDragon().isPresent()
                    && EntityLookup.byUUID(level.getServer(), bondData.currentBondedDragon().get()) instanceof DragonEntity;
            boolean rebondBlocked = bondData.hasHadBondedDragonDie() && !com.dragonspeech.config.DragonSpeechConfig.allowRebondAfterDeath();

            if (!alreadyBonded && !rebondBlocked) {
                dragon.setBondedOwner(placer.getUUID());
                bonded = true;
            }
        }

        level.addFreshEntity(dragon);

        if (bonded && placer != null) {
            PlayerBondData bondData = PlayerBondAccess.get(placer);
            PlayerBondAccess.set(placer, bondData.withCurrentBondedDragon(dragon.getUUID()));
            placer.sendSystemMessage(Component.literal("The shell cracks. A hatchling looks up at you - bonded, for as long as you both live."));
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(placer,
                    new com.dragonspeech.network.OpenDragonBondScreenPayload(dragon.getUUID()));
        } else if (placer != null) {
            placer.sendSystemMessage(Component.literal("The shell cracks. Whatever emerges looks at you without recognition - it answers to no one."));
        }
    }
}
