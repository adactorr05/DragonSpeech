package com.dragonspeech.dragon.egg;

import com.dragonspeech.dragon.breed.DragonBreed;
import com.dragonspeech.dragon.breed.DragonBreedRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * "I do like their hatching system so I will use that" per explicit
 * direction - the HABITAT CONCEPT (per-position condition scoring,
 * see com.dragonspeech.dragon.egg.Habitat and its implementations) is
 * a faithful port of Dragon Mounts Legacy's own system (GPL-3.0). This
 * class - the actual TICKING/HATCHING MECHANIC that consumes those
 * habitat points - is a genuine redesign, not a port, and worth being
 * explicit about why.
 *
 * Checked Dragon Mounts Legacy's own HatchableEggBlockEntity/
 * HatchableEggBlock directly: their habitat points ONLY ever decide
 * "which breed does this currently-unknown/generic egg become,"
 * updated periodically via updateHabitat() - a completely separate
 * concern from the actual hatch-chance roll in randomTick(), which
 * uses a flat, breed-defined probability (hatchChance()) totally
 * independent of habitat. That's because Dragon Mounts Legacy's own
 * eggs are randomly-bred/generic until habitat conditions "discover"
 * a breed for them.
 *
 * This project's eggs are the opposite - each one is already a
 * specific, fixed breed from the moment it's created (color chosen at
 * crafting, not randomly rolled - see DragonEggBlockItem). So "use
 * their hatching system" for THIS mod's eggs has to mean something
 * different: habitat points for the egg's OWN, already-known breed
 * gate/scale how fast it actually hatches, not which breed it becomes.
 * That's what this class does - not what Dragon Mounts Legacy's own
 * code does with habitats, even though the underlying Habitat scoring
 * mechanism itself is unchanged from theirs.
 *
 * HATCH_STAGE (0-3) and the crack/hatch progression concept ARE kept
 * from their design - that part translates directly regardless of
 * what drives the chance roll.
 */
public class DragonEggHatchingBlockEntity extends BlockEntity {

    public static final int MIN_HABITAT_POINTS = 2;
    public static final int MAX_HATCH_STAGE = 3;

    private static final String NBT_BREED = "Breed";
    private static final String NBT_HATCHING = "Hatching";
    private static final String NBT_HATCH_STAGE = "HatchStage";
    private static final String NBT_PLACED_BY = "PlacedBy";
    private static final String NBT_SEED = "Seed";
    private static final String NBT_NEVER_BONDABLE = "NeverBondable";

    private ResourceLocation breedId;
    private boolean hatching;
    private int hatchStage;
    private Optional<UUID> placedBy = Optional.empty();
    private long seed;
    private boolean neverBondable;

    public DragonEggHatchingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        if (breedId != null) {
            tag.putString(NBT_BREED, breedId.toString());
        }
        tag.putBoolean(NBT_HATCHING, hatching);
        tag.putInt(NBT_HATCH_STAGE, hatchStage);
        placedBy.ifPresent(uuid -> tag.putUUID(NBT_PLACED_BY, uuid));
        tag.putLong(NBT_SEED, seed);
        tag.putBoolean(NBT_NEVER_BONDABLE, neverBondable);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        if (tag.contains(NBT_BREED)) {
            breedId = ResourceLocation.tryParse(tag.getString(NBT_BREED));
        }
        hatching = tag.getBoolean(NBT_HATCHING);
        hatchStage = tag.getInt(NBT_HATCH_STAGE);
        placedBy = tag.hasUUID(NBT_PLACED_BY) ? Optional.of(tag.getUUID(NBT_PLACED_BY)) : Optional.empty();
        seed = tag.getLong(NBT_SEED);
        neverBondable = tag.getBoolean(NBT_NEVER_BONDABLE);
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public long seed() {
        return seed;
    }

    public void setNeverBondable(boolean neverBondable) {
        this.neverBondable = neverBondable;
    }

    public boolean isNeverBondable() {
        return neverBondable;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, lookup);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void setBreedId(ResourceLocation breedId) {
        this.breedId = breedId;
    }

    @Nullable
    public ResourceLocation getBreedId() {
        return breedId;
    }

    @Nullable
    public DragonBreed getBreed() {
        return breedId == null ? null : DragonBreedRegistry.get(breedId);
    }

    public void setPlacedBy(UUID uuid) {
        this.placedBy = Optional.of(uuid);
    }

    public Optional<UUID> placedBy() {
        return placedBy;
    }

    public boolean isHatching() {
        return hatching;
    }

    /** Right-click on the placed egg starts the process - matches Dragon Mounts Legacy's own trigger exactly (a placed egg sits inert until a player activates it, it doesn't start counting down the moment it's placed). */
    public void beginHatching() {
        this.hatching = true;
        setChanged();
    }

    public int hatchStage() {
        return hatchStage;
    }

    public boolean isFinalStage() {
        return hatchStage >= MAX_HATCH_STAGE;
    }

    public void advanceStage() {
        hatchStage = Math.min(MAX_HATCH_STAGE, hatchStage + 1);
        setChanged();
    }
}
