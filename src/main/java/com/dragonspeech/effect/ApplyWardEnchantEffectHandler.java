package com.dragonspeech.effect;

import com.dragonspeech.DragonSpeech;
import com.dragonspeech.enchant.MagicEnchantment;
import com.dragonspeech.enchant.MagicEnchantments;
import com.dragonspeech.enchant.WardPowerSource;
import com.dragonspeech.storage.DragonSpeechComponents;
import com.dragonspeech.storage.SkillsAccess;
import com.dragonspeech.ward.WardType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * NOW GENERALIZED (was hardcoded to "galdrverja" alone) - registered
 * once per ward word, same "one reusable class, many configured
 * instances" pattern ApplyBlessingOrCurseEffectHandler already
 * established. galdrverja itself stays generic (wardType == null,
 * absorbs any wardable damage); the 4 newer words (eldgaldr, hoggaldr,
 * sprengaldr, fallgaldr) are damage-type-specific, each requiring the
 * matching lightweight verja-family word as a prerequisite (eldverja,
 * hoggverja, sprengverja, fallverja respectively) - see each word's own
 * JSON for the exact prerequisite_words entry.
 *
 * Self-targeting, same as draga/ChargeItemEffectHandler.
 *
 * "letta [word]" strips this specific ward off the held item, same
 * pattern "letta skjoldr"/"letta marka" already use. Checks `removable`
 * first (always true for a ward right now, unless Curse of the Grave
 * has locked the item down).
 *
 * POWER SOURCE: "aflbinda" spoken alongside this -> STAMINA_LINKED.
 * Otherwise -> DURABILITY. Re-speaking the SAME word on an item that
 * already carries it overwrites just the power source in place, per
 * the explicit "overwrite by changing how it draws its power" rule -
 * but a DIFFERENT ward word stacks alongside it as its own separate
 * entry, since multiple different wards on one item are allowed.
 */
public class ApplyWardEnchantEffectHandler implements EffectHandler {

    /** Deliberately far above any other spell's cost in this mod - "consumes a massive amount of stamina" was explicit. */
    private static final float ENCHANT_BASE_COST = 120f;
    private static final float WARD_DURABILITY_MAX = 200f;

    private final ResourceLocation id;
    private final String wordId;
    /** null = generic (galdrverja); otherwise the specific damage type this ward matches. */
    private final WardType wardType;
    private final EffectHandlerCaps caps;

    public ApplyWardEnchantEffectHandler(String handlerId, String wordId, WardType wardType) {
        this.id = DragonSpeech.id(handlerId);
        this.wordId = wordId;
        this.wardType = wardType;
        this.caps = new EffectHandlerCaps(1, ENCHANT_BASE_COST, 1f, Set.of(TargetKind.ENTITY));
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public EffectHandlerCaps caps() {
        return caps;
    }

    @Override
    public boolean selfTargeting() {
        return true;
    }

    @Override
    public float estimateBaseMagnitude(EffectInvocation invocation) {
        boolean dispel = invocation.composition().words().stream().anyMatch(w -> "letta".equals(w.trueName()));
        return dispel ? 2f : ENCHANT_BASE_COST;
    }

    @Override
    public EffectResult apply(EffectInvocation invocation) {
        ServerPlayer caster = invocation.caster();

        if (!SkillsAccess.get(caster).canEnchant()) {
            return EffectResult.failure(
                "You do not yet know how to bind a working into an object - the shape of the word slips away from you.");
        }

        ItemStack held = caster.getMainHandItem();
        if (held.isEmpty()) {
            return EffectResult.failure("You hold nothing to bind a working into.");
        }

        boolean dispel = invocation.composition().words().stream().anyMatch(w -> "letta".equals(w.trueName()));
        if (dispel) {
            return applyDispel(held);
        }

        boolean staminaLinked = invocation.composition().words().stream().anyMatch(w -> "aflbinda".equals(w.trueName()));
        boolean reserveLinked = invocation.composition().words().stream().anyMatch(w -> "afla".equals(w.trueName()));
        WardPowerSource powerSource = staminaLinked ? WardPowerSource.STAMINA_LINKED
            : (reserveLinked ? WardPowerSource.RESERVE : WardPowerSource.DURATION);
        long expiresAt = powerSource == WardPowerSource.DURATION
            ? caster.level().getGameTime() + 20L * 45L
            : -1L;

        MagicEnchantments existing = held.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
        MagicEnchantment entry = wardType == null
            ? MagicEnchantment.newWard(wordId, powerSource, WARD_DURABILITY_MAX, 1, expiresAt, caster.getUUID().toString())
            : MagicEnchantment.newTypedWard(wordId, powerSource, WARD_DURABILITY_MAX, 1, wardType, expiresAt, caster.getUUID().toString());

        MagicEnchantments updated = existing.has(wordId)
            ? existing.replacing(wordId, entry)
            : existing.with(entry);

        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, updated);

        String sourceText = switch (powerSource) {
            case STAMINA_LINKED -> "bound directly to your stamina";
            case RESERVE, DURABILITY -> "filled with an independent afla reserve";
            case DURATION -> "held by duration alone";
        };
        String message = (existing.has(wordId)
            ? "The ward already bound here unravels and reweaves, now "
            : "A ward binds itself into what you hold, ") + sourceText + ".";

        return EffectResult.success(1, message);
    }

    /** "letta [word]" - strips this ward off the held item, if it's there and removable. */
    private EffectResult applyDispel(ItemStack held) {
        MagicEnchantments existing = held.getOrDefault(DragonSpeechComponents.MAGIC_ENCHANTMENTS, MagicEnchantments.EMPTY);
        var entry = existing.find(wordId);

        if (entry.isEmpty()) {
            return EffectResult.failure("What you hold carries no such ward to release.");
        }
        if (!entry.get().removable()) {
            return EffectResult.failure("This is bound into what you hold beyond any unbinding - the word finds no purchase.");
        }

        held.set(DragonSpeechComponents.MAGIC_ENCHANTMENTS, existing.without(wordId));
        return EffectResult.success(1, "The ward unwinds from what you hold and fades.");
    }
}
