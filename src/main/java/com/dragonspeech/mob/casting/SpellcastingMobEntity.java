package com.dragonspeech.mob.casting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * Shared base for the NEUTRAL spellcasting humanoids - Elf, Elder Elf, and
 * Human Mage all extend this directly. Shade does NOT: it needs to extend
 * Monster for vanilla hostile-mob classification, and Java has no multiple
 * inheritance, so ShadeEntity implements SpellcastingMob directly instead
 * and holds its own SpellcasterState field - see that class's comment for
 * why the state itself lives in a separate object rather than here.
 *
 * Also implements Merchant (word-for-emerald trading, per the design
 * brief's "elves and Humans will sometimes have trades to trade for
 * different words") since every current subclass of this class IS a
 * neutral, tradeable mob - Shade being the one spellcasting mob that
 * ISN'T tradeable is exactly why it lives outside this hierarchy. See
 * MobTradeOffers for how the actual offer list is built, and
 * canTrade()/WordScrollItem for the rest of the flow.
 *
 * VERSION-RISK NOTE: mobInteract's InteractionResult return type and the
 * Merchant interface's exact member list are both copied from/matched
 * against DragonEntity.mobInteract, which is the one already-working
 * precedent for this exact API shape elsewhere in this codebase - see
 * that method if this doesn't compile cleanly. Merchant's default
 * openTradingScreen(Player, Component, int) is relied on rather than
 * reimplemented, since that default has been stable Mojang-side for a
 * long time and doing the OptionalInt/sendMerchantOffers dance by hand
 * would only add more version-risk surface for no benefit.
 */
public abstract class SpellcastingMobEntity extends PathfinderMob implements SpellcastingMob, Merchant {

    private final SpellcasterState spellState;

    @Nullable
    private Player tradingPlayer;
    private int villagerXp;

    // Bug fix: getOffers() must return the SAME object across a trading
    // session - vanilla mutates/tracks offers in place (offer.increaseUses(),
    // selection-index tracking). Returning a fresh MerchantOffers/MerchantOffer
    // graph on every call (the original approach) desyncs that and causes
    // the trade screen to open and immediately auto-close. Cached here,
    // rebuilt only when trading starts with a (possibly new) player, and
    // cleared when trading ends so the NEXT session still reflects any
    // words learned since.
    @Nullable
    private MerchantOffers cachedOffers;
    @Nullable
    private java.util.UUID cachedOffersFor;

    protected SpellcastingMobEntity(EntityType<? extends PathfinderMob> type, Level level, MobPowerTier tier) {
        super(type, level);
        this.spellState = new SpellcasterState(tier);
    }

    /** Call from the subclass constructor with that race/variant's starting word pool - see MobWordPools. */
    protected final void applyStartingVocabulary(List<ResourceLocation> pool) {
        spellState.vocabulary().learnAll(pool);
    }

    /** Whether this individual mob currently offers trades at all - overridden by HumanMageEntity, which shouldn't trade while its per-instance hostile roll is active. Always true otherwise. */
    protected boolean canTrade() {
        return true;
    }

    @Override
    public final SpellcasterState spellState() {
        return spellState;
    }

    @Override
    public final LivingEntity asEntity() {
        return this;
    }

    /**
     * "They despawn too fast. They should stay in the game longer...
     * similar to villagers" per direction. Villagers themselves are
     * ALWAYS persistence-required in vanilla (they never randomly
     * despawn from distance/time) - matching that exactly is simpler and
     * more predictable than a separate "only after being traded with"
     * flag, and it's the same behavior the direction's own analogy
     * points at.
     */
    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        spellState.tick();
    }

    /**
     * "They should manage their stamina... this management can also be
     * dependent on pressure" per direction - this is where that pressure
     * signal actually gets recorded. A plain Elf/Human Mage has no ward
     * to apply, so this is their only hurt() override; Elder Elf's own
     * override (see ElderElfEntity) applies wards FIRST and calls
     * super.hurt() with the reduced amount, which lands here - so a
     * well-warded Elder Elf correctly registers less pressure from a
     * blocked hit than an unwarded one would from the same swing.
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        recordDamagePressure(amount);
        return super.hurt(source, amount);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // CONFIRMED FIX: the log showed mobInteract firing TWICE per
        // click, once per hand, both timestamped identically - the
        // second call's openTradingScreen() was immediately superseding
        // (closing) the screen the first call had just opened, which is
        // exactly "opens for a split second, then vanishes." Only ever
        // acting on MAIN_HAND stops the second call from doing anything.
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide && canTrade() && player.getItemInHand(hand).isEmpty() && !this.isVehicle()) {
            try {
                this.setTradingPlayer(player);
                MerchantOffers offers = this.getOffers();
                if (offers.isEmpty()) {
                    // Diagnostic breakdown right in the message itself -
                    // confirmed useful last round (showed the exact
                    // "you already know everything" cause immediately).
                    String diagnosis = MobTradeOffers.diagnose(this, player);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        this.getDisplayName().getString() + " has nothing left to teach you right now. (" + diagnosis + ")"));
                    this.setTradingPlayer(null);
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
                this.openTradingScreen(player, this.getDisplayName(), 1);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            } catch (Exception e) {
                com.dragonspeech.DragonSpeech.LOGGER.error("[DragonSpeech] Trade screen failed to open", e);
                this.setTradingPlayer(null);
                return InteractionResult.FAIL;
            }
        }
        return super.mobInteract(player, hand);
    }

    // ---------------------------------------------------------------
    // Merchant
    // ---------------------------------------------------------------

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
        if (player == null) {
            this.cachedOffers = null;
            this.cachedOffersFor = null;
        }
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    /**
     * Deliberately rebuilt live on every call rather than cached/persisted:
     * MobTradeOffers already filters out anything the current
     * tradingPlayer already knows, so a bought word simply stops being
     * offered to that specific player on their very next visit - no
     * separate "offer already used" bookkeeping to save/load, and no
     * risk of a stale offer list surviving a word being learned some
     * other way (guessing, a tablet, admin grant...).
     */
    @Override
    public MerchantOffers getOffers() {
        java.util.UUID currentPlayerId = tradingPlayer != null ? tradingPlayer.getUUID() : null;
        if (cachedOffers == null || !java.util.Objects.equals(cachedOffersFor, currentPlayerId)) {
            cachedOffers = MobTradeOffers.build(this, tradingPlayer);
            cachedOffersFor = currentPlayerId;
        }
        return cachedOffers;
    }

    /** No-op: see getOffers() - there is no stored list to override, offers are always derived fresh from vocabulary. */
    @Override
    public void overrideOffers(MerchantOffers offers) {
    }

    /** The real teaching happens when the player USES the resulting Word Scroll (see WordScrollItem) - this just plays feedback and nudges the offer list to refresh for any UI still open. increaseUses() is MerchantOffer's own long-standing "mark this trade as used once" method; if renamed in your mappings this is a one-line fix. */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.level().playSound(null, this.blockPosition(), getNotifyTradeSound(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return villagerXp;
    }

    @Override
    public void overrideXp(int xp) {
        this.villagerXp = xp;
    }

    @Override
    public boolean showProgressBar() {
        return true;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean canRestock() {
        return false;
    }

    /**
     * VERSION-RISK NOTE: Entity's own level-accessor was renamed
     * getLevel() -> level() at some point, and Merchant's interface may
     * or may not have followed suit for this exact method (and for
     * isClientSide() below) - genuinely unclear without a decompiled jar.
     * Deliberately NOT marked @Override: if Merchant still wants
     * getLevel(), this satisfies it either way; if Merchant now uses
     * level() directly (already provided by Entity/PathfinderMob with
     * zero extra code needed), this is just a harmless unused method
     * rather than a hard "method does not override anything" error.
     */
    public Level getLevel() {
        return this.level();
    }

    public boolean isClientSide() {
        return this.level().isClientSide;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("dragonspeech_spellcaster", spellState.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("dragonspeech_spellcaster")) {
            spellState.load(tag.getCompound("dragonspeech_spellcaster"));
        }
    }
}

