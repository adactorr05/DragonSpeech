package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.enchant.MagicEnchantment;
import com.dragonspeech.enchant.MagicEnchantments;
import com.dragonspeech.enchant.WardPowerSource;
import com.dragonspeech.stamina.PlayerMagicData;
import com.dragonspeech.stamina.StaminaAccess;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.storage.ItemStaminaStorage;
import com.dragonspeech.storage.StorageMediumRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;

/**
 * Backs "draga" - transfers the caster's own stamina into whatever
 * they're holding. Self-targeting: you work this word on yourself, not
 * on something you're looking at.
 *
 * The pipeline's normal drain (via estimateBaseMagnitude) covers only
 * the small OVERHEAD of the working; the actual transferred energy is
 * moved 1:1 inside apply(), directly stamina -> item, deliberately NOT
 * through DrainResolver - charging a gem should never spill into your
 * hunger or hearts, and the transfer itself must stay 1:1 regardless of
 * attunement/precision discounts (only the overhead gets cheaper with
 * skill, never the exchange rate - otherwise practiced casters could
 * mint energy from nothing by round-tripping it).
 *
 * TWO DESTINATIONS, chosen by what else is in the sentence rather than
 * a second dedicated word - "words compose, they don't multiply the
 * dictionary," same as every other modifier-shaped addition in this
 * grammar:
 *
 *   "draga"                - the item's GENERAL stored-stamina pool
 *                             (StorageMediumRegistry media - gems,
 *                             jewelry's own capacity), unchanged
 *                             behavior from before this addition.
 *   "draga galdrverja"      - if the held item ALREADY carries a
 *                             galdrverja ward, refills THAT ward's own
 *                             durability specifically instead of the
 *                             item's general pool. Only meaningful for
 *                             DURABILITY-sourced wards - a
 *                             stamina-linked one has no pool to refill
 *                             at all, it just always draws on you live.
 *
 * WARD_WORDS is the small, explicitly-extended set of "this word names
 * a specific ward that can be refilled this way" - only galdrverja
 * exists so far (Phase 1's one proof-of-concept ward). Every future
 * specific ward word needs adding here too, or "draga
 * <thatWord>" will just fall through to the general-pool behavior
 * instead of refilling that ward specifically.
 */
public class ChargeItemEffectHandler implements EffectHandler {

    private static final float TRANSFER_PER_CAST = 10f;

    private static final Set<String> WARD_WORDS = Set.of("galdrverja", "eldgaldr", "hoggaldr", "sprengaldr", "fallgaldr");

    private static final EffectHandlerCaps CAPS = new EffectHandlerCaps(
        1, TRANSFER_PER_CAST, 1f, Set.of(TargetKind.ENTITY)
    );

    @Override
    public ResourceLocation id() {
        return DragonSpeech.id("charge_item");
    }

    @Override
    public EffectHandlerCaps caps() {
        return CAPS;
    }

    @Override
    public boolean selfTargeting() {
        return true;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        return 2f; // the overhead of the working, not the transfer itself
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();
        ItemStack held = caster.getMainHandItem();

        if (held.isEmpty()) {
            return EffectResult.failure("You hold nothing to give your strength to.");
        }

        Optional<String> wardWord = invocation.composition().words().stream()
            .map(w -> w.trueName())
            .filter(WARD_WORDS::contains)
            .findFirst();

        if (wardWord.isPresent()) {
            return chargeSpecificWard(caster, held, wardWord.get());
        }
        return chargeGeneralPool(caster, held);
    }

    private EffectResult chargeGeneralPool(ServerPlayer caster, ItemStack held) {
        if (!StorageMediumRegistry.isValidMedium(held.getItem())) {
            return EffectResult.failure("You must hold something able to keep your strength.");
        }

        PlayerMagicData magic = StaminaAccess.get(caster);
        float toTransfer = Math.min(TRANSFER_PER_CAST, magic.stamina());
        if (toTransfer <= 0f) {
            return EffectResult.failure("You have no strength left to give.");
        }

        float actuallyStored = ItemStaminaStorage.charge(held, toTransfer);
        if (actuallyStored <= 0f) {
            return EffectResult.failure("It can hold no more.");
        }

        StaminaAccess.set(caster, magic.withStamina(magic.stamina() - actuallyStored));
        return EffectResult.success(actuallyStored, "Strength flows from you into the stone.");
    }

    private EffectResult chargeSpecificWard(ServerPlayer caster, ItemStack held, String wardWordId) {
        MagicEnchantments enchantments = held.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
        Optional<MagicEnchantment> ward = enchantments.find(wardWordId);

        if (ward.isEmpty()) {
            return EffectResult.failure("What you hold carries no such ward to feed.");
        }
        if (ward.get().powerSource() == WardPowerSource.STAMINA_LINKED) {
            return EffectResult.failure("That ward draws on you directly - it has no pool of its own to fill.");
        }
        if (ward.get().durabilityCurrent() >= ward.get().durabilityMax()) {
            return EffectResult.failure("That ward already holds all the strength it can.");
        }

        PlayerMagicData magic = StaminaAccess.get(caster);
        float room = ward.get().durabilityMax() - ward.get().durabilityCurrent();
        float toTransfer = Math.min(Math.min(TRANSFER_PER_CAST, magic.stamina()), room);
        if (toTransfer <= 0f) {
            return EffectResult.failure("You have no strength left to give.");
        }

        MagicEnchantment refilled = ward.get().withDurability(ward.get().durabilityCurrent() + toTransfer);
        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, enchantments.replacing(wardWordId, refilled));
        StaminaAccess.set(caster, magic.withStamina(magic.stamina() - toTransfer));

        return EffectResult.success(toTransfer, "Strength flows from you into the ward bound within it.");
    }
}
